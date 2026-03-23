plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

group = "com.example"
version = "0.0.1"

application {
    mainClass = "ApplicationKt"
    applicationDefaultJvmArgs =
        if (providers.gradleProperty("disableSfg").isPresent)
            listOf("-Dio.ktor.internal.disable.sfg=true")
        else
            emptyList()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.opentelemetry.extension.kotlin)
    implementation(libs.opentelemetry.instrumentation.ktor)
    implementation(libs.opentelemetry.sdk)

    testImplementation(libs.ktor.server.testing)
    testImplementation(kotlin("test"))
}
