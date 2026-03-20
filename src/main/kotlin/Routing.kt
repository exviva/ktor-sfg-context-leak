import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Application.configureRouting() {
    routing {
        get("/") {
            val switchContexts = call.queryParameters["i"] == "4"
            if (switchContexts) {
                withContext(Dispatchers.IO) {}
            }

            val callTraceId =
                call
                    .attributes
                    .allKeys
                    .find { it.name == "OpenTelemetry" }
                    ?.let {
                        @Suppress("UNCHECKED_CAST")
                        it as AttributeKey<Context>
                    }
                    ?.let(call.attributes::get)
                    ?.let(Span::fromContext)
                    ?.spanContext
                    ?.traceId
                    ?: "?".repeat(32)

            call.respondText(
                "trace_id=$callTraceId" +
                    " thread_name=${Thread.currentThread().name}" +
                    " switchContexts=$switchContexts"
            )
        }
    }
}
