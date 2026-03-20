plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

group = "com.example"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.slf4j)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.extension.kotlin)
    implementation(libs.opentelemetry.instrumentation.ktor)
    implementation(libs.opentelemetry.instrumentation.logback)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.sdk.logs)

    testImplementation(libs.ktor.server.testing)
    testImplementation(kotlin("test"))
}
