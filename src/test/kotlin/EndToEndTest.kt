import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.lang.Thread.sleep
import java.net.ConnectException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EndToEndTest {
    private lateinit var serverProcess: Process
    val httpClient = HttpClient()
    val url = "http://localhost:8080/"

    @AfterTest
    fun stopServer() {
        serverProcess.destroy()

        while (serverProcess.isAlive) {
            println("Waiting for server PID=${serverProcess.pid()} to terminate...")
            sleep(1000)
        }
    }

    @Test
    fun `trace ID preserved when request handled by thread which switched contexts in the past`() {
        startServer()
        loopRequests()
    }

    @Test
    fun `trace ID preserved with SuspendFunctionGun disabled`() {
        startServer("-PdisableSfg")
        loopRequests()
    }

    private fun loopRequests() = runBlocking {
        List(12) { i -> httpClient.get(url) { parameter("i", i) } }
            .map { it.bodyAsText() }
            .onEach(::println)
            .forEachIndexed { i, responseBody ->
                assertTrue(
                    traceIdRegex.containsMatchIn(responseBody),
                    "Invalid trace ID in response #$i: $responseBody"
                )
            }
    }

    private fun startServer(vararg gradleArgs: String) = runBlocking {
        serverProcess = runGradleApp(*gradleArgs)

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

    private fun runGradleApp(vararg gradleArgs: String): Process {
        val processArgs = listOf(
            "./gradlew",
            "-Dorg.gradle.logging.level=quiet",
            "--quiet",
            *gradleArgs,
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