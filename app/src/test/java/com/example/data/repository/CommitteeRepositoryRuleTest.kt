package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CommitteeRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createStartBidAndDeclareWinner_endToEndCalculationAndSync() = runBlocking {
    val adminUid = signInTestUser(ADMIN_EMAIL)
    val repo = CommitteeRepository(firestore, auth)

    val committeeId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createCommittee(
        name = "हमारी BC कमेटी",
        totalBcAmount = 100_000L,
        totalMembers = 10L,
        timerMinutes = 7L,
        creatorName = "राजेश शर्मा",
        creatorMobile = "9876543210",
        creatorEmail = ADMIN_EMAIL
      ).getOrThrow()
    }
    assertTrue(committeeId.isNotBlank())

    val committees = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMyCommittees(adminUid).first { list -> list.any { it.id == committeeId } }
    }
    val committee = committees.first { it.id == committeeId }
    assertEquals(7L, committee.timerMinutes)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.startLiveBidding(committee, customMinutes = 7L).getOrThrow()
    }

    val liveCommittees = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMyCommittees(adminUid).first { list ->
        list.firstOrNull { it.id == committeeId }?.isLive == true
      }
    }
    val liveCommittee = liveCommittees.first { it.id == committeeId }

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.placeBid(liveCommittee, 20_000L, "राजेश शर्मा", "9876543210").getOrThrow()
    }

    val updatedCommittee = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMyCommittees(adminUid).first { list ->
        list.firstOrNull { it.id == committeeId }?.currentHighestBid == 20_000L
      }.first { it.id == committeeId }
    }

    val members = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMembers(committeeId, adminUid).first { it.isNotEmpty() }
    }

    val roundRecord = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.lockAndDeclareWinner(updatedCommittee, members).getOrThrow()
    }
    assertEquals(20_000L, roundRecord.winningBid)
    assertEquals(80_000L, roundRecord.netPayableToWinner)
    assertEquals(8_000L, roundRecord.perMemberInstallment)
    assertTrue(roundRecord.sloganText.contains("YOU ARE GREAT"))
  }

  @Test
  fun observeCommittees_crossUserIsolation_enforced() = runBlocking {
    val adminUid = signInTestUser(ADMIN_EMAIL)
    val adminRepo = CommitteeRepository(firestore, auth)
    val committeeId = withTimeout(DEFAULT_TIMEOUT_MS) {
      adminRepo.createCommittee(
        name = "निजी BC",
        totalBcAmount = 50_000L,
        totalMembers = 5L,
        timerMinutes = 3L,
        creatorName = "एडमिन",
        creatorMobile = "9000000001",
        creatorEmail = ADMIN_EMAIL
      ).getOrThrow()
    }

    val outsiderUid = signInTestUser(OUTSIDER_EMAIL)
    val outsiderRepo = CommitteeRepository(firestore, auth)
    val outsiderList = withTimeout(FLOW_TIMEOUT_MS) {
      outsiderRepo.observeMyCommittees(outsiderUid).first()
    }
    assertTrue(outsiderList.none { it.id == committeeId })
    assertTrue(adminUid != outsiderUid)
  }

  @Test
  fun observeCommittees_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = CommitteeRepository(firestore, auth)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeMyCommittees("fake_uid").first()
      }
      fail("Expected PERMISSION_DENIED when unauthenticated")
    } catch (e: Exception) {
      val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }
  }

  private companion object {
    const val ADMIN_EMAIL = "rajesh.admin@test.com"
    const val OUTSIDER_EMAIL = "outsider.bob@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
