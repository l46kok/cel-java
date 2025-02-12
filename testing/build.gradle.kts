plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.guava)
    implementation(libs.junit)
    implementation(libs.protobuf.java)
    implementation(libs.truth)
    implementation(project(":common"))
    implementation(project(":generated"))
    implementation(project(":runtime"))

    compileOnlyApi(libs.auto.value.annotations)
    annotationProcessor(libs.auto.value)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
