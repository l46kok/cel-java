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
    testImplementation(project(":generated"))

    implementation(libs.guava)
    implementation(libs.jspecify)
    implementation(libs.threeten.extra)
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.java.util)
    implementation(libs.re2j)
    implementation(project(":common"))
    implementation(project(":parser")) // This is needed due to CelAttributeParser. Need to decouple

    compileOnlyApi(libs.auto.value.annotations)
    annotationProcessor(libs.auto.value)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
