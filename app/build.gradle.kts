plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":notes"))
    implementation(project(":assessment"))
    implementation(project(":scheduling"))
    implementation(project(":infrastructure"))
    implementation("io.ktor:ktor-server-core-jvm:3.6.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.6.0")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.6.0")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.6.0")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("ch.qos.logback:logback-classic:1.5.20")
}

application {
    mainClass.set("dev.repetition.app.ApplicationKt")
}
