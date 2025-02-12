plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.protobuf.java)
    implementation(libs.antlr4.runtime)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
