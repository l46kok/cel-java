plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(project(":testing"))

    compileOnlyApi(libs.auto.value.annotations)
    annotationProcessor(libs.auto.value)

    implementation(libs.antlr4.runtime)
    implementation(libs.guava)
    implementation(libs.jspecify)
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.java.util)
    implementation(libs.re2j)
    implementation(libs.test.parameter.injector)
    implementation(project(":generated"))
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
