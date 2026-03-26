import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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

                withContext(threadLocal.asVerboseContextElement("Created in $path")) {
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

data class ThreadLocalKey(private val threadLocal: ThreadLocal<*>) : CoroutineContext.Key<VerboseThreadLocalElement<*>>

class VerboseThreadLocalElement<T>(
    private val value: T,
    private val threadLocal: ThreadLocal<T>
) : ThreadContextElement<T> {
    override val key: CoroutineContext.Key<*> = ThreadLocalKey(threadLocal)

    override fun updateThreadContext(context: CoroutineContext): T {
        val oldState = threadLocal.get()

        logger.info("updateThreadContext {} => {}", oldState, value)
        threadLocal.set(value)
        return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: T) {
        logger.info("restoreThreadContext {} => {}", threadLocal.get(), oldState)
        threadLocal.set(oldState)
    }

    // this method is overridden to perform value comparison (==) on key
    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext {
        return if (this.key == key) EmptyCoroutineContext else this
    }

    // this method is overridden to perform value comparison (==) on key
    override operator fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? =
        @Suppress("UNCHECKED_CAST")
        if (this.key == key) this as E else null

    override fun toString(): String = "VerboseThreadLocal(tl = ${threadLocal.get()})"

    companion object {
        private val logger = LoggerFactory.getLogger(VerboseThreadLocalElement::class.java)
    }
}

fun <T> ThreadLocal<T>.asVerboseContextElement(value: T = get()) = VerboseThreadLocalElement(value, this)