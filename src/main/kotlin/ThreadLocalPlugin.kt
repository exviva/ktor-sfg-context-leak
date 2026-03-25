import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

val ThreadLocalPlugin = createApplicationPlugin("ThreadLocalPlugin", ::ThreadLocalPluginConfig) {
    val startPhase = PipelinePhase("ThreadLocal")
    val threadLocal = pluginConfig.threadLocal

    application.insertPhaseBefore(ApplicationCallPipeline.Setup, startPhase)
    application.intercept(startPhase) {
        val path = call.request.path()
        val tlValue = threadLocal.get()
        val message = "${Thread.currentThread().name} serving $path"

        print("[$message] ")
        if (tlValue == null) {
            println("all healthy, thread local is null outside withContext")

            withContext(threadLocal.asContextElement(message)) {
                proceed()
            }
        } else {
            println("thread local LEAKED: '$tlValue'")

            proceed()
        }
    }
}

class ThreadLocalPluginConfig<T> {
    lateinit var threadLocal: ThreadLocal<T>
}