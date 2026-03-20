package com.example

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Thread.sleep

fun Application.configureRouting() {
    routing {
        get("/") {
            call.application.log.info("hello from route")
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
                    ?: "NULL"

            call.application.log.info("route: otel context in call attributes: $callTraceId")
            call.respondText("Hello World!")
            withContext(Dispatchers.IO) {
                sleep(10)
            }
        }
    }
}
