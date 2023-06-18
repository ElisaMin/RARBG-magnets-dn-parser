plugins {
    kotlin("jvm") version "1.8.21"
    application
}

group = "me.heizi.lab.rarbg"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
//    implementation("org.ufoss.kotysa:kotysa-core:3.0.2"
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    //slf4j
    implementation("org.slf4j:slf4j-api:2.0.0-alpha5")
    implementation("org.slf4j:slf4j-nop:2.0.0-alpha5")
}
kotlin {
    jvmToolchain(19)
    target {
        compilations {
            all {
                kotlinOptions {
                    freeCompilerArgs = listOf("-Xcontext-receivers")
                }
            }
        }
    }
}


application {
    mainClass.set("MainKt")
}
