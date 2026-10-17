package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSignalAndChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createSignalAndObserve_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSignalAndChatRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSignalLog(
                ticker = "SBER",
                stockName = "Сбербанк",
                signalType = "LONG",
                entryPrice = 285.0,
                takeProfit = 291.0,
                stopLoss = 282.0,
                confidence = 88,
                reason = "Пробой EMA 20 вверх"
            )
        }
        assertTrue(createResult.isSuccess)
        val signalId = createResult.getOrThrow()

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserSignals(aliceUid).first { list -> list.any { it.id == signalId } }
        }
        assertTrue(emitted.any { it.id == signalId && it.ticker == "SBER" })
    }

    @Test
    fun getSignalById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSignalAndChatRepository(firestore)
        val signalId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createSignalLog(
                ticker = "LKOH",
                stockName = "ЛУКОЙЛ",
                signalType = "SHORT",
                entryPrice = 7100.0,
                takeProfit = 6950.0,
                stopLoss = 7175.0,
                confidence = 82,
                reason = "Пробой EMA 20 вниз"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSignalAndChatRepository(firestore)
        val bobResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getSignalById(aliceUid, signalId)
        }
        assertTrue(bobResult.isFailure)
        val ex = bobResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeUserSignals_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = CloudSignalAndChatRepository(firestore)

        var caughtCode: FirebaseFirestoreException.Code? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeUserSignals("unauthenticated_uid").first()
            }
        } catch (e: Throwable) {
            val firestoreEx = generateSequence(e) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            caughtCode = firestoreEx?.code
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caughtCode)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@moexpulse.test"
        const val BOB_EMAIL = "bob@moexpulse.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
