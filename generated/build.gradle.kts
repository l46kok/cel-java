plugins {
    `java-library`
}

repositories {
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
