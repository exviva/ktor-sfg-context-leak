import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Application.configureRouting(threadLocal: ThreadLocal<String?>) {
    routing {
        get("/thread_local/{i}") {
            val i = call.pathParameters["i"]
            val switchContexts = i == "4"
            if (switchContexts) {
                withContext(Dispatchers.IO) {}
            }

            call.respondText(
                listOf(
                    "i=$i",
                    "thread_local='${threadLocal.get()}'",
                    "thread_name=${Thread.currentThread().name}",
                    "switch_contexts=$switchContexts"
                ).joinToString(" ")
            )
        }
    }
}
