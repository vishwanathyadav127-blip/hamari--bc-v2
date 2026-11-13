package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BcCalculatorTest {
  @Test
  fun calculateBreakdown_matchesExpectedWinnerNetAndInstallment() {
    val (netPayable, perMember) = BcCalculator.calculateBreakdown(
      totalBcAmount = 100_000L,
      totalMembers = 10L,
      winningBid = 20_000L
    )
    assertEquals(80_000L, netPayable)
    assertEquals(8_000L, perMember)
  }

  @Test
  fun formatRupees_usesIndianNumberingSystem() {
    assertEquals("₹1,00,000", BcCalculator.formatRupees(100_000L))
    assertEquals("₹80,000", BcCalculator.formatRupees(80_000L))
    assertEquals("₹8,000", BcCalculator.formatRupees(8_000L))
    assertEquals("₹500", BcCalculator.formatRupees(500L))
  }

  @Test
  fun formatSlogan_replacesWinnerPlaceholder() {
    val result = BcCalculator.formatSlogan(
      Committee.DEFAULT_SLOGAN_TEMPLATE,
      "रमेश गुप्ता"
    )
    assertTrue(result.contains("रमेश गुप्ता"))
    assertTrue(result.contains("YOU ARE GREAT"))
  }
}
