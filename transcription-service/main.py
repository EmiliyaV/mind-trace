import asyncio
import io
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from faster_whisper import WhisperModel
from pydantic import BaseModel

MAX_AUDIO_BYTES = 20 * 1024 * 1024
ALLOWED_AUDIO_TYPES = {
    "audio/wav",
    "audio/x-wav",
    "audio/mpeg",
    "audio/mp3",
    "audio/webm",
    "audio/ogg",
    "audio/mp4",
    "audio/x-m4a",
    "audio/flac",
}
model = None
inference_lock = asyncio.Lock()


class TranscriptionResponse(BaseModel):
    text: str


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model
    model = await asyncio.to_thread(
        WhisperModel,
        os.getenv("WHISPER_MODEL", "base"),
        device=os.getenv("WHISPER_DEVICE", "cpu"),
        compute_type=os.getenv("WHISPER_COMPUTE_TYPE", "int8"),
        download_root=os.getenv("WHISPER_MODEL_DIR", "/models"),
    )
    yield
    model = None


app = FastAPI(title="Local transcription service", lifespan=lifespan)


@app.get("/health")
async def health():
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    return {"status": "READY"}


async def receive_audio(request: Request) -> bytes:
    received = bytearray()
    async for chunk in request.stream():
        if len(received) + len(chunk) > MAX_AUDIO_BYTES:
            raise HTTPException(status_code=413, detail="Audio exceeds 20 MiB")
        received.extend(chunk)
    if not received:
        raise HTTPException(status_code=400, detail="Audio must not be empty")
    return bytes(received)


def decode_and_transcribe(audio: bytes) -> str:
    language = os.getenv("WHISPER_LANGUAGE", "").strip() or None
    segments, _ = model.transcribe(
        io.BytesIO(audio),
        beam_size=1,
        language=language,
        vad_filter=True,
    )
    return " ".join(segment.text.strip() for segment in segments).strip()


@app.post("/transcribe", response_model=TranscriptionResponse)
async def transcribe(request: Request):
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    content_type = request.headers.get("content-type", "").split(";", 1)[0].strip().lower()
    if content_type not in ALLOWED_AUDIO_TYPES:
        raise HTTPException(status_code=415, detail="Unsupported audio type")
    audio = await receive_audio(request)
    async with inference_lock:
        try:
            text = await asyncio.to_thread(decode_and_transcribe, audio)
        except Exception as error:
            raise HTTPException(status_code=422, detail="Audio cannot be decoded") from error
    if not text:
        raise HTTPException(status_code=422, detail="Speech was not recognized")
    return TranscriptionResponse(text=text)
