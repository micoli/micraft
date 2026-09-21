package org.micoli.micraft.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

class RunTickGuardedTest {

    @Test
    fun `reports a regular failure and returns`() =
        runBlocking<Unit> {
            val errors = mutableListOf<Throwable>()

            runTickGuarded({ error("boom") }) { errors += it }

            assertEquals(listOf("boom"), errors.map { it.message })
        }

    @Test
    fun `swallows a cancellation from a closed socket so the loop survives`() =
        runBlocking<Unit> {
            val errors = mutableListOf<Throwable>()

            runTickGuarded({ throw CancellationException("socket closed") }) { errors += it }

            assertEquals(1, errors.size)
        }

    @Test
    fun `propagates when the loop coroutine itself is cancelled`() =
        runBlocking<Unit> {
            val started = CompletableDeferred<Unit>()
            val loop =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runTickGuarded({
                        started.complete(Unit)
                        awaitCancellation()
                    }) {}
                }
            started.await()
            loop.cancel()

            assertFailsWith<CancellationException> { loop.await() }
        }
}
