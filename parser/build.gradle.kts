plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.test.parameter.injector)

    implementation(libs.antlr4.runtime)
    implementation(libs.guava)
    implementation(libs.protobuf.java)
    implementation(libs.re2j)
    implementation(libs.threeten.extra)
    implementation(project(":common"))
    implementation(project(":generated"))

    compileOnlyApi(libs.auto.value.annotations)
    annotationProcessor(libs.auto.value)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
