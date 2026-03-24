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
EndToEndTest > thread-local doesn't leak with SuspendFunctionGun enabled STANDARD_OUT
    i=0 thread_local='eventLoopGroupProxy-4-2 serving /thread_local/0' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false
    i=1 thread_local='eventLoopGroupProxy-4-3 serving /thread_local/1' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    i=2 thread_local='eventLoopGroupProxy-4-1 serving /thread_local/2' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    i=3 thread_local='eventLoopGroupProxy-4-2 serving /thread_local/3' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false

    # This thread local leaks to subsequent requests on that thread
    i=4 thread_local='eventLoopGroupProxy-4-3 serving /thread_local/4' thread_name=eventLoopGroupProxy-4-3 switch_contexts=true
    i=5 thread_local='eventLoopGroupProxy-4-1 serving /thread_local/5' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    i=6 thread_local='eventLoopGroupProxy-4-2 serving /thread_local/6' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false

    # e.g. here...
    i=7 thread_local='eventLoopGroupProxy-4-3 serving /thread_local/4' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    i=8 thread_local='eventLoopGroupProxy-4-1 serving /thread_local/8' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    i=9 thread_local='eventLoopGroupProxy-4-2 serving /thread_local/9' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false

    # or here
    i=10 thread_local='eventLoopGroupProxy-4-3 serving /thread_local/4' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    i=11 thread_local='eventLoopGroupProxy-4-1 serving /thread_local/11' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false

EndToEndTest > thread-local doesn't leak with SuspendFunctionGun enabled FAILED
    java.lang.AssertionError: Expected the char sequence to contain the substring.
    CharSequence <i=7 thread_local='eventLoopGroupProxy-4-3 serving /thread_local/4' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false>, substring <serving /thread_local/7>
```

While the test with SFG disabled is passing:

```
EndToEndTest > thread-local doesn't leak with SuspendFunctionGun disabled STANDARD_OUT
    i=0 thread_local='eventLoopGroupProxy-4-2 serving /thread_local/0' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false
    i=1 thread_local='eventLoopGroupProxy-4-3 serving /thread_local/1' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    i=2 thread_local='eventLoopGroupProxy-4-1 serving /thread_local/2' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    i=3 thread_local='eventLoopGroupProxy-4-2 serving /thread_local/3' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false
    i=4 thread_local='eventLoopGroupProxy-4-3 serving /thread_local/4' thread_name=eventLoopGroupProxy-4-3 switch_contexts=true
    i=5 thread_local='eventLoopGroupProxy-4-1 serving /thread_local/5' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    i=6 thread_local='eventLoopGroupProxy-4-2 serving /thread_local/6' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false
    i=7 thread_local='eventLoopGroupProxy-4-3 serving /thread_local/7' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    i=8 thread_local='eventLoopGroupProxy-4-1 serving /thread_local/8' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
    i=9 thread_local='eventLoopGroupProxy-4-2 serving /thread_local/9' thread_name=eventLoopGroupProxy-4-2 switch_contexts=false
    i=10 thread_local='eventLoopGroupProxy-4-3 serving /thread_local/10' thread_name=eventLoopGroupProxy-4-3 switch_contexts=false
    i=11 thread_local='eventLoopGroupProxy-4-1 serving /thread_local/11' thread_name=eventLoopGroupProxy-4-1 switch_contexts=false
```

Here are the plugin's server logs when running `./gradlew run` and `for i in {0..9}; do http :8080/thread_local/$i | grep thread_local=; done`:

```
[eventLoopGroupProxy-4-1 serving /thread_local/0] all healthy, thread local is null outside withContext
[eventLoopGroupProxy-4-2 serving /thread_local/1] all healthy, thread local is null outside withContext
[eventLoopGroupProxy-4-3 serving /thread_local/2] all healthy, thread local is null outside withContext
[eventLoopGroupProxy-4-1 serving /thread_local/3] all healthy, thread local is null outside withContext
[eventLoopGroupProxy-4-2 serving /thread_local/4] all healthy, thread local is null outside withContext
[eventLoopGroupProxy-4-3 serving /thread_local/5] all healthy, thread local is null outside withContext
[eventLoopGroupProxy-4-1 serving /thread_local/6] all healthy, thread local is null outside withContext
[eventLoopGroupProxy-4-2 serving /thread_local/7] thread local LEAKED: 'eventLoopGroupProxy-4-2 serving /thread_local/4'
[eventLoopGroupProxy-4-3 serving /thread_local/8] all healthy, thread local is null outside withContext
[eventLoopGroupProxy-4-1 serving /thread_local/9] all healthy, thread local is null outside withContext
```

### What could be going on?

The `SuspendFunctionGun` pipeline executor doesn't properly unmount thread-local context elements when the executed coroutine switches contexts.