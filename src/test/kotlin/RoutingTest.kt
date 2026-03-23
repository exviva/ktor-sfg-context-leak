import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertTrue

val traceIdRegex = """call_trace_id=\w{32} """.toRegex()

class RoutingTest {

    // Unfortunately, this test is always passing likely due to the way testApplication
    // is using some test coroutine dispatcher which has different behaviour than
    // a production coroutine runtime.
    @Test
    fun `trace ID gone when request handled by thread which switched contexts in the past`() =
        testApplication {
            application { module() }

            repeat(32) { i ->
                val response = client.get("/") {
                    parameter("i", i)
                    header("traceparent", "00-${i.toString().padEnd(32, '5')}-0000111122223333-01")
                }
                val responseBody = response.bodyAsText()

                assertTrue(
                    traceIdRegex.containsMatchIn(responseBody),
                    "Invalid trace ID in: $responseBody"
                )
            }
        }
}