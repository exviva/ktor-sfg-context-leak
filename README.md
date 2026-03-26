# Ktor context leakage with SuspendFunctionGun

Possible repro of:
* https://youtrack.jetbrains.com/projects/KTOR/issues/KTOR-6118/CallMonitoring-SuspendFunctionGun-sometimes-leaks-coroutine-context
* https://youtrack.jetbrains.com/issue/KTOR-6802/Autoreloading-Consistent-ThreadLocal-coroutine-context-leak-with-SuspendFunctionGun
* https://youtrack.jetbrains.com/issue/KTOR-7053/ktor-server-call-logging-MDC-does-not-clear-information-for-subsequent-requests

The issue seems to be affecting several Ktor plugins which use thread-locals, e.g. Otel, MDC, etc.

I was able to reproduce this using the following versions:

```toml
kotlin = "2.3.20"
ktor = "3.4.1"
```

To demonstrate the issue, execute `./gradlew -i test`. One of the e2e tests is failing, e.g.:

```
EndToEndTest > thread-local doesn't leak with SuspendFunctionGun enabled STANDARD_ERROR
    [Test worker @coroutine#19] INFO EndToEndTest - i=0 thread_local='Created in /thread_local/0' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false
    [Test worker @coroutine#19] INFO EndToEndTest - i=1 thread_local='Created in /thread_local/1' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    [Test worker @coroutine#19] INFO EndToEndTest - i=2 thread_local='Created in /thread_local/2' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    [Test worker @coroutine#19] INFO EndToEndTest - i=3 thread_local='Created in /thread_local/3' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false

    # This thread local leaks to subsequent requests on that thread
    [Test worker @coroutine#19] INFO EndToEndTest - i=4 thread_local='Created in /thread_local/4' thread_name=eventLoopGroupProxy-4-3 switch_contexts=true
    [Test worker @coroutine#19] INFO EndToEndTest - i=5 thread_local='Created in /thread_local/5' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    [Test worker @coroutine#19] INFO EndToEndTest - i=6 thread_local='Created in /thread_local/6' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false

    # e.g. here...
    [Test worker @coroutine#19] INFO EndToEndTest - i=7 thread_local='Created in /thread_local/4' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    [Test worker @coroutine#19] INFO EndToEndTest - i=8 thread_local='Created in /thread_local/8' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    [Test worker @coroutine#19] INFO EndToEndTest - i=9 thread_local='Created in /thread_local/9' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false

    # and here
    [Test worker @coroutine#19] INFO EndToEndTest - i=10 thread_local='Created in /thread_local/4' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    [Test worker @coroutine#19] INFO EndToEndTest - i=11 thread_local='Created in /thread_local/11' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false

EndToEndTest > thread-local doesn't leak with SuspendFunctionGun enabled FAILED
    java.lang.AssertionError: Expected the char sequence to contain the substring.
    CharSequence <i=7 thread_local='Created in /thread_local/4' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false>, substring <thread_local='Created in /thread_local/7'>, ignoreCase <false>.
```

While the test with SFG disabled is passing:

```
EndToEndTest > thread-local doesn't leak with SuspendFunctionGun disabled STANDARD_ERROR
    [Test worker @coroutine#114] INFO EndToEndTest - i=0 thread_local='Created in /thread_local/0' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false
    [Test worker @coroutine#114] INFO EndToEndTest - i=1 thread_local='Created in /thread_local/1' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    [Test worker @coroutine#114] INFO EndToEndTest - i=2 thread_local='Created in /thread_local/2' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    [Test worker @coroutine#114] INFO EndToEndTest - i=3 thread_local='Created in /thread_local/3' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false
    [Test worker @coroutine#114] INFO EndToEndTest - i=4 thread_local='Created in /thread_local/4' thread_name=eventLoopGroupProxy-4-3 switch_contexts=true
    [Test worker @coroutine#114] INFO EndToEndTest - i=5 thread_local='Created in /thread_local/5' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    [Test worker @coroutine#114] INFO EndToEndTest - i=6 thread_local='Created in /thread_local/6' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false
    [Test worker @coroutine#114] INFO EndToEndTest - i=7 thread_local='Created in /thread_local/7' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    [Test worker @coroutine#114] INFO EndToEndTest - i=8 thread_local='Created in /thread_local/8' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    [Test worker @coroutine#114] INFO EndToEndTest - i=9 thread_local='Created in /thread_local/9' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false
    [Test worker @coroutine#114] INFO EndToEndTest - i=10 thread_local='Created in /thread_local/10' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    [Test worker @coroutine#114] INFO EndToEndTest - i=11 thread_local='Created in /thread_local/11' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
```

Here are the plugin's server logs when running `./gradlew run` and `for i in {0..9}; do http :8080/thread_local/$i | grep thread_local=; done`:

```
[eventLoopGroupProxy-4-1] INFO Application - [/thread_local/0] thread local is null
[eventLoopGroupProxy-4-2] INFO Application - [/thread_local/1] thread local is null
[eventLoopGroupProxy-4-3] INFO Application - [/thread_local/2] thread local is null
[eventLoopGroupProxy-4-1] INFO Application - [/thread_local/3] thread local is null
[eventLoopGroupProxy-4-2] INFO Application - [/thread_local/4] thread local is null
[eventLoopGroupProxy-4-2] WARN Application - [/thread_local/4] thread local NOT NULL after withContext: 'Created in /thread_local/4'
[eventLoopGroupProxy-4-3] INFO Application - [/thread_local/5] thread local is null
[eventLoopGroupProxy-4-1] INFO Application - [/thread_local/6] thread local is null
[eventLoopGroupProxy-4-2] WARN Application - [/thread_local/7] thread local LEAKED: 'Created in /thread_local/4'
[eventLoopGroupProxy-4-3] INFO Application - [/thread_local/8] thread local is null
[eventLoopGroupProxy-4-1] INFO Application - [/thread_local/9] thread local is null
```

Here are logs from the `VerboseThreadLocalElement` around request #4:

```
# Request #3
[eventLoopGroupProxy-4-1] INFO Application - [/thread_local/3] thread local is null
[eventLoopGroupProxy-4-1] INFO VerboseThreadLocalElement - updateThreadContext null => Created in /thread_local/3
[eventLoopGroupProxy-4-1] INFO VerboseThreadLocalElement - restoreThreadContext Created in /thread_local/3 => null

# Request #4
[eventLoopGroupProxy-4-2] INFO Application - [/thread_local/4] thread local is null
[eventLoopGroupProxy-4-2] INFO VerboseThreadLocalElement - updateThreadContext null => Created in /thread_local/4
[eventLoopGroupProxy-4-2] INFO VerboseThreadLocalElement - restoreThreadContext Created in /thread_local/4 => null
[DefaultDispatcher-worker-1] INFO VerboseThreadLocalElement - updateThreadContext null => Created in /thread_local/4
[DefaultDispatcher-worker-1] INFO VerboseThreadLocalElement - restoreThreadContext Created in /thread_local/4 => null

# Two subsequent calls to `updateThreadContext` without a matching `restoreThreadContext`
[eventLoopGroupProxy-4-2] INFO VerboseThreadLocalElement - updateThreadContext null => Created in /thread_local/4
[eventLoopGroupProxy-4-2] INFO VerboseThreadLocalElement - updateThreadContext Created in /thread_local/4 => Created in /thread_local/4
[eventLoopGroupProxy-4-2] INFO VerboseThreadLocalElement - restoreThreadContext Created in /thread_local/4 => Created in /thread_local/4
[eventLoopGroupProxy-4-2] INFO VerboseThreadLocalElement - updateThreadContext Created in /thread_local/4 => Created in /thread_local/4
[eventLoopGroupProxy-4-2] INFO VerboseThreadLocalElement - restoreThreadContext Created in /thread_local/4 => Created in /thread_local/4
[eventLoopGroupProxy-4-2] INFO VerboseThreadLocalElement - updateThreadContext Created in /thread_local/4 => Created in /thread_local/4
[eventLoopGroupProxy-4-2] INFO VerboseThreadLocalElement - restoreThreadContext Created in /thread_local/4 => Created in /thread_local/4
[eventLoopGroupProxy-4-2] WARN Application - [/thread_local/4] thread local NOT NULL after withContext: 'Created in /thread_local/4'

# Request #5
[eventLoopGroupProxy-4-3] INFO Application - [/thread_local/5] thread local is null
[eventLoopGroupProxy-4-3] INFO VerboseThreadLocalElement - updateThreadContext null => Created in /thread_local/5
[eventLoopGroupProxy-4-3] INFO VerboseThreadLocalElement - restoreThreadContext Created in /thread_local/5 => null
```

### What could be going on?

The `SuspendFunctionGun` pipeline executor doesn't properly unmount thread-local context elements when the executed coroutine switches contexts.