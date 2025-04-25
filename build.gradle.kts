
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "tel.schich"
version = "1.0-SNAPSHOT"

tasks.withType<Test> {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
    jvm {

    }
    js {
        browser {
            webpackTask {
                mainOutputFileName = "regexstar.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinxHtml)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {}

        val jvmTest by getting {

        }
    }
}