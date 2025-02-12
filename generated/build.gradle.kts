plugins {
    `java-library`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    implementation(libs.protobuf.java)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
