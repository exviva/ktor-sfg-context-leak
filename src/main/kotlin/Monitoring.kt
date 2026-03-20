import io.ktor.server.application.*
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk

fun Application.configureMonitoring() {
    install(KtorServerTelemetry) {
        val openTelemetry = OpenTelemetrySdk.builder().build()

        setOpenTelemetry(openTelemetry)
    }
}
