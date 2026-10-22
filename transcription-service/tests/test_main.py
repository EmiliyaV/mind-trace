import asyncio
import unittest
from unittest.mock import patch

from fastapi import HTTPException
from starlette.requests import Request

from main import receive_audio, decode_and_transcribe


def make_request(chunks):
    iterator = iter(chunks)

    async def receive():
        try:
            return {"type": "http.request", "body": next(iterator), "more_body": True}
        except StopIteration:
            return {"type": "http.request", "body": b"", "more_body": False}

    return Request({"type": "http", "method": "POST", "headers": []}, receive)


class TranscriptionTests(unittest.TestCase):
    def test_rejects_empty_body(self):
        with self.assertRaises(HTTPException) as raised:
            asyncio.run(receive_audio(make_request([])))
        self.assertEqual(400, raised.exception.status_code)

    def test_rejects_oversized_body(self):
        with patch("main.MAX_AUDIO_BYTES", 3):
            with self.assertRaises(HTTPException) as raised:
                asyncio.run(receive_audio(make_request([b"ab", b"cd"])))
        self.assertEqual(413, raised.exception.status_code)

    def test_collects_audio_without_saving_to_disk(self):
        self.assertEqual(b"abcd", asyncio.run(receive_audio(make_request([b"ab", b"cd"]))))

    def test_joins_segments(self):
        class Segment:
            def __init__(self, text):
                self.text = text

        class Model:
            def transcribe(self, audio, **kwargs):
                return iter([Segment(" Hello "), Segment(" world ")]), {}

        with patch("main.model", Model()):
            self.assertEqual("Hello world", decode_and_transcribe(b"audio"))
