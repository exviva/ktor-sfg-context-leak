import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ConnectException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EndToEndTest {
    private lateinit var serverProcess: Process
    val httpClient = HttpClient()
    val url = "http://localhost:8080/"

    @BeforeTest
    fun runServer(): Unit = runBlocking {
        if (!isServerReady()) {
            serverProcess = runGradleApp()
        } else {
            println("Server already running, skipping gradle run")
        }

        var attempt = 0

        withTimeout(15.seconds) {
            while (true) {
                if (isServerReady()) {
                    println("Connected to server after $attempt attempts")
                    break
                } else {
                    println("Attempt #$attempt: server not ready, connection refused")
                    attempt++
                    delay(500.milliseconds)
                }
            }
        }
    }

    @AfterTest
    fun stopServer() = if (::serverProcess.isInitialized) serverProcess.destroy() else Unit

    @Test
    fun `trace ID preserved when request handled by thread which switched contexts in the past`() =
        runBlocking {
            List(12) { i ->
                httpClient
                    .get(url) {
                        parameter("i", i)
                    }
                    .bodyAsText()
            }
                .onEach(::println)
                .forEachIndexed { i, responseBody ->
                    assertTrue(
                        traceIdRegex.containsMatchIn(responseBody),
                        "Invalid trace ID in response #$i: $responseBody"
                    )
                }
        }

    private fun runGradleApp(): Process {
        val processArgs = listOf(
            "./gradlew",
            "-Dorg.gradle.logging.level=quiet",
            "--quiet",
            "run"
        )

        return ProcessBuilder(processArgs).start()
    }

    private suspend fun isServerReady() = try {
        httpClient.options(url)
        true
    } catch (_: ConnectException) {
        false
    }
}