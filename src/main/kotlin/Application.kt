import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    val tl = ThreadLocal<String>()

    install(ThreadLocalPlugin) {
        this.threadLocal = tl
    }

    configureRouting(tl)
}
