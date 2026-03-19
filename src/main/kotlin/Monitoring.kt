package com.example

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext

fun Application.configureMonitoring() {
    install(CallLogging) {
        format { call ->
            runBlocking(Span.current().asContextElement() + MDCContext()) {
                listOf(
                    call.request.httpMethod.value,
                    call.request.uri,
                    call.response.status(),
                    call.processingTimeMillis(),
                    call.receiveText().ifEmpty { "[empty body]" }
                ).joinToString(separator = ", ")
            }
        }
    }

    install(KtorServerTelemetry) {
        val loggerProvider =
            SdkLoggerProvider.builder()
                .addLogRecordProcessor(
                    BatchLogRecordProcessor.builder(OtlpGrpcLogRecordExporter.builder().build()).build()
                )
                .build()

        val openTelemetry = OpenTelemetrySdk.builder()
            .setLoggerProvider(loggerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build()

        setOpenTelemetry(openTelemetry)
    }
}
