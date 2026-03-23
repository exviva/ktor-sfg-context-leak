import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

fun Context.traceId(): String =
    Span.fromContext(this).spanContext.traceId

fun Application.configureRouting() {
    routing {
        get("/") {
            val switchContexts = call.queryParameters["i"] == "4"
            if (switchContexts) {
                withContext(Dispatchers.IO) {}
            }

            val ccTraceId = currentCoroutineContext()
                .getOpenTelemetryContext()
                .traceId()

            val rootContextTraceId = Context.root().traceId()

            val callAttributeKeys = call
                .attributes
                .allKeys
                .map { it.name }

            val callTraceId = call
                .attributes
                .allKeys
                .find { it.name == "OpenTelemetry" }
                ?.let {
                    @Suppress("UNCHECKED_CAST")
                    it as AttributeKey<Context>
                }
                ?.let(call.attributes::get)
                ?.let(Context::traceId)
                ?: "?".repeat(32)

            call.respondText(
                listOf(
                    "call_trace_id=$callTraceId",
                    "cc_trace_id=$ccTraceId",
                    "thread_name=${Thread.currentThread().name}",
                    "switchContexts=$switchContexts",
                    "call_attribute_keys=$callAttributeKeys",
                    "root_trace_id=$rootContextTraceId"
                ).joinToString(" ")
            )
        }
    }
}
