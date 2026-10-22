plugins {
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
}

allprojects {
    group = "dev.repetition"
    version = "0.1.0"
    repositories {
        mavenCentral()
    }
}
