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
    call_trace_id=936f30a79746a106e327dbb4de35b1b6 cc_trace_id=936f30a79746a106e327dbb4de35b1b6 thread_name=eventLoopGroupProxy-4-2 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=2badf82f7d95277c18894e1f92ca09d9 cc_trace_id=2badf82f7d95277c18894e1f92ca09d9 thread_name=eventLoopGroupProxy-4-3 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=c947a1060cc82e1726176f577c64571c cc_trace_id=c947a1060cc82e1726176f577c64571c thread_name=eventLoopGroupProxy-4-1 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=833adea6d68c21a70985ff10509021a3 cc_trace_id=833adea6d68c21a70985ff10509021a3 thread_name=eventLoopGroupProxy-4-2 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000

    # withContext pollutes subsequent requests on eventLoopGroupProxy-4-3
    call_trace_id=c39b3c0ef0b93965f54ea7abedf9cd8a cc_trace_id=c39b3c0ef0b93965f54ea7abedf9cd8a thread_name=eventLoopGroupProxy-4-3 switchContexts=true call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=3c5e377fc2a2b9a50b14f3a0aefedd51 cc_trace_id=3c5e377fc2a2b9a50b14f3a0aefedd51 thread_name=eventLoopGroupProxy-4-1 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=861f2a8e9909f8840261a8a73cc5a51a cc_trace_id=861f2a8e9909f8840261a8a73cc5a51a thread_name=eventLoopGroupProxy-4-2 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000

    # here
    call_trace_id=???????????????????????????????? cc_trace_id=00000000000000000000000000000000 thread_name=eventLoopGroupProxy-4-3 switchContexts=false call_attribute_keys=[EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=7ef89ab3742f64ba179960c31af992a2 cc_trace_id=7ef89ab3742f64ba179960c31af992a2 thread_name=eventLoopGroupProxy-4-1 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=3b4bf25041215bfee195149fd210c42e cc_trace_id=3b4bf25041215bfee195149fd210c42e thread_name=eventLoopGroupProxy-4-2 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000

    # and here
    call_trace_id=???????????????????????????????? cc_trace_id=00000000000000000000000000000000 thread_name=eventLoopGroupProxy-4-3 switchContexts=false call_attribute_keys=[EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=8699ea6435fe824cfb4675b63c664c05 cc_trace_id=8699ea6435fe824cfb4675b63c664c05 thread_name=eventLoopGroupProxy-4-1 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000

EndToEndTest > trace ID preserved when request handled by thread which switched contexts in the past FAILED
    java.lang.AssertionError: Invalid trace ID in response #7: call_trace_id=???????????????????????????????? cc_trace_id=00000000000000000000000000000000 thread_name=eventLoopGroupProxy-4-3 switchContexts=false call_attribute_keys=[EngineResponse] root_trace_id=00000000000000000000000000000000
        ...
        at EndToEndTest.loopRequests(EndToEndTest.kt:42)
        at EndToEndTest.trace ID preserved when request handled by thread which switched contexts in the past(EndToEndTest.kt:33)
```

While the test with SFG disabled is passing:

```
EndToEndTest > trace ID preserved with SuspendFunctionGun disabled STANDARD_OUT
    call_trace_id=0d93c9a2b043af3d99b52f363bce0b4f cc_trace_id=0d93c9a2b043af3d99b52f363bce0b4f thread_name=eventLoopGroupProxy-4-2 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=b188b0c6758dc4e7480a0596f3f95849 cc_trace_id=b188b0c6758dc4e7480a0596f3f95849 thread_name=eventLoopGroupProxy-4-3 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=0006e83e15d619c9f0039e7ff97aa0a4 cc_trace_id=0006e83e15d619c9f0039e7ff97aa0a4 thread_name=eventLoopGroupProxy-4-1 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=9b09f96029318e5e1ee764182acc7364 cc_trace_id=9b09f96029318e5e1ee764182acc7364 thread_name=eventLoopGroupProxy-4-2 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000

    # In this case, withContext...
    call_trace_id=18a991e72d958a08b6db7fff73059ca1 cc_trace_id=18a991e72d958a08b6db7fff73059ca1 thread_name=eventLoopGroupProxy-4-3 switchContexts=true call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=0e3e152b3d06def49ba62207769c2060 cc_trace_id=0e3e152b3d06def49ba62207769c2060 thread_name=eventLoopGroupProxy-4-1 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=2f040e6696b559ff7c33ac0c3c6c1d37 cc_trace_id=2f040e6696b559ff7c33ac0c3c6c1d37 thread_name=eventLoopGroupProxy-4-2 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000

    # ...doesn't affect subsequent requests on this thread
    call_trace_id=b044db3b1c970cb0fff05980a50c388f cc_trace_id=b044db3b1c970cb0fff05980a50c388f thread_name=eventLoopGroupProxy-4-3 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=d528c67c7a8e6fa48c133378c8df089c cc_trace_id=d528c67c7a8e6fa48c133378c8df089c thread_name=eventLoopGroupProxy-4-1 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=da18385ced2b7416a2117d174d87078e cc_trace_id=da18385ced2b7416a2117d174d87078e thread_name=eventLoopGroupProxy-4-2 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=3e523ffb55c7c90e60f3193dd36d1014 cc_trace_id=3e523ffb55c7c90e60f3193dd36d1014 thread_name=eventLoopGroupProxy-4-3 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
    call_trace_id=3cd7f067d1fa05e10eb9bfc603138ca2 cc_trace_id=3cd7f067d1fa05e10eb9bfc603138ca2 thread_name=eventLoopGroupProxy-4-1 switchContexts=false call_attribute_keys=[OpenTelemetry, EngineResponse] root_trace_id=00000000000000000000000000000000
```

### What could be going on?

The Ktor Server otel plugin creates a new Context for every request. It then [stores](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v2.26.1/instrumentation/ktor/ktor-common-2.0/library/src/main/kotlin/io/opentelemetry/instrumentation/ktor/common/v2_0/internal/KtorServerTelemetryUtil.kt#L41-L45) the context in `call.attributes` (under the `OpenTelemetry` key),
and as a `KotlinContextElement` coroutine context element (subclass of `ThreadContextElement<Scope>`). In the test, it's suspicious that
the otel context is missing from both the call attributes (`call_trace_id=????????????????????????????????`), and the coroutine context (`cc_trace_id=00000000000000000000000000000000`)!
It's almost as if it's not the previous otel context leaking across requests, but the plugin's `intercept(startPhase)` block is never executed.

Unfortunately, I wasn't able to debug the plugin, installing the debugger java agent also installs the coroutine debugging runtime, under which the issue doesn't happen.