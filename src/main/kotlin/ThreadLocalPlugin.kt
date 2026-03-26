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

        threadLocal
            .get()
            ?.also { tlValue ->
                application.log.warn("[{}] thread local LEAKED: '{}'", path, tlValue)

                proceed()
            }
            ?: run {
                application.log.info("[{}] thread local is null", path)

                withContext(threadLocal.asContextElement("Created in $path")) {
                    proceed()
                }

                threadLocal
                    .get()
                    ?.also { tlValue ->
                        application.log.warn(
                            "[{}] thread local NOT NULL after withContext: '{}'",
                            path,
                            tlValue
                        )
                    }
            }
    }
}

class ThreadLocalPluginConfig {
    lateinit var threadLocal: ThreadLocal<String?>
}