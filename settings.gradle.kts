plugins {
    // Downloads a matching JDK automatically when the required toolchain is not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "antifraud"

include("contracts")
include("transaction-generator")
