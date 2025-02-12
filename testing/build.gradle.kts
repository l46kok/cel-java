plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.protobuf.java)
//    implementation(libs.test.parameter.injector)
    implementation(libs.junit)
    implementation(libs.guava)
    implementation(project(":common"))
    implementation(project(":generated"))
    testImplementation(libs.truth)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
