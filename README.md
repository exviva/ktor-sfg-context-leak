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
opentelemetry = "1.60.1"
opentelemetry-instrumentation = "2.24.0-alpha"
```

To demonstrate the issue, execute `./gradlew -i test`. One of the e2e tests is failing, e.g.:

```
EndToEndTest > trace ID preserved when request handled by thread which switched contexts in the past STANDARD_OUT
    trace_id=3b6f8c8dbbd1e7db9639873eccde930a thread_name=eventLoopGroupProxy-4-2 switchContexts=false
    trace_id=3e21ac8210ae94657f90e1fdbca0201f thread_name=eventLoopGroupProxy-4-3 switchContexts=false
    trace_id=736ac2fc8a9567479d361fa7ac3e59fb thread_name=eventLoopGroupProxy-4-1 switchContexts=false
    trace_id=d423b21ed3ec851d94d84b5a8592911b thread_name=eventLoopGroupProxy-4-2 switchContexts=false

    # withContext pollutes subsequent requests on eventLoopGroupProxy-4-3
    trace_id=2103860c90265887b7404a02e3ebedfb thread_name=eventLoopGroupProxy-4-3 switchContexts=true
    trace_id=cbb33efd19c2b74b59dfb191e59a073d thread_name=eventLoopGroupProxy-4-1 switchContexts=false
    trace_id=2f00250a86432b6ec633ed45c8449dad thread_name=eventLoopGroupProxy-4-2 switchContexts=false

    # here
    trace_id=???????????????????????????????? thread_name=eventLoopGroupProxy-4-3 switchContexts=false
    trace_id=5c02d96b776c4e92a6e158c4ac3ebbc5 thread_name=eventLoopGroupProxy-4-1 switchContexts=false
    trace_id=c12b121428f137e55b25d923552c54dc thread_name=eventLoopGroupProxy-4-2 switchContexts=false

    # and here
    trace_id=???????????????????????????????? thread_name=eventLoopGroupProxy-4-3 switchContexts=false
    trace_id=7285609750c285c6e4dba66a376f4e4e thread_name=eventLoopGroupProxy-4-1 switchContexts=false

EndToEndTest > trace ID preserved when request handled by thread which switched contexts in the past FAILED
    java.lang.AssertionError: Invalid trace ID in response #7: trace_id=???????????????????????????????? thread_name=eventLoopGroupProxy-4-3 switchContexts=false
        ...
        at EndToEndTest.loopRequests(EndToEndTest.kt:42)
        at EndToEndTest.trace ID preserved when request handled by thread which switched contexts in the past(EndToEndTest.kt:33)
```

While the test with SFG disabled is passing:

```
EndToEndTest > trace ID preserved with SuspendFunctionGun disabled STANDARD_OUT
    trace_id=3671a15d79128bdd9efee0ea352285b3 thread_name=eventLoopGroupProxy-4-2 switchContexts=false
    trace_id=19acc2495cd5d146524a8162466aff55 thread_name=eventLoopGroupProxy-4-3 switchContexts=false
    trace_id=79b32a7d73c99b1f96ed20fd1460a93e thread_name=eventLoopGroupProxy-4-1 switchContexts=false
    trace_id=4cd5e15185541362cfd5142e20ae569b thread_name=eventLoopGroupProxy-4-2 switchContexts=false

    # In this case, withContext...
    trace_id=d79eec832d3b6692d9f7f687cd1d17f7 thread_name=eventLoopGroupProxy-4-3 switchContexts=true
    trace_id=291fc8870bb488bc54673d11933a1559 thread_name=eventLoopGroupProxy-4-1 switchContexts=false
    trace_id=b97dec1a0ca45b750f8622fbf65cf077 thread_name=eventLoopGroupProxy-4-2 switchContexts=false

    # ...doesn't affect subsequent requests on this thread
    trace_id=eaf5ff851a228e876ddc44db710463f4 thread_name=eventLoopGroupProxy-4-3 switchContexts=false
    trace_id=8edd23d6f89d05d485aefa2158a2b29a thread_name=eventLoopGroupProxy-4-1 switchContexts=false
    trace_id=c4d10a2de4448b66f687418aab2b4f73 thread_name=eventLoopGroupProxy-4-2 switchContexts=false
    trace_id=b3564da0085fa9f2a822662622bfba1b thread_name=eventLoopGroupProxy-4-3 switchContexts=false
    trace_id=cc7120456b5d1536ca41533f2f2cff82 thread_name=eventLoopGroupProxy-4-1 switchContexts=false
```