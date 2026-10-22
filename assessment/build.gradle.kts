plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
    implementation(project(":notes"))
    implementation(project(":scheduling"))
}

tasks.test {
    useJUnitPlatform()
}
