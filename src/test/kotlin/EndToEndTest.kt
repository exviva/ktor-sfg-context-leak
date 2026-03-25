import io.ktor.client.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.net.ConnectException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger(EndToEndTest::class.java)
private val httpClient = HttpClient {
    defaultRequest {
        host = "localhost"
        port = 8080
    }
}

class EndToEndTest {
    private lateinit var serverProcess: Process

    @AfterTest
    fun stopServer() {
        serverProcess.destroy()

        while (serverProcess.isAlive) {
            logger.info("Waiting for server PID={} to terminate...", serverProcess.pid())
            sleep(1000)
        }
    }

    @Test
    fun `thread-local doesn't leak with SuspendFunctionGun enabled`() {
        startServer()
        loopRequests()
    }

    @Test
    fun `thread-local doesn't leak with SuspendFunctionGun disabled`() {
        startServer("-PdisableSfg")
        loopRequests()
    }

    private fun loopRequests() = runBlocking {
        List(12) { "/thread_local/$it" }
            .associateWith { httpClient.get(it) }
            .mapValues { it.value.bodyAsText() }
            .onEach { logger.info(it.value) }
            .forEach { (path, responseBody) ->
                assertContains(responseBody, "thread_local='Created in $path'")
            }
    }

    private fun startServer(vararg gradleArgs: String) = runBlocking {
        serverProcess = runGradleApp(*gradleArgs)

        var attempt = 0

        withTimeout(15.seconds) {
            while (true) {
                if (isServerReady()) {
                    logger.info("Connected to server after {} attempts", attempt)
                    break
                } else {
                    logger.warn("Attempt #{}: server not ready, connection refused", attempt)
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
        httpClient.options("/")
        true
    } catch (_: ConnectException) {
        false
    }
}