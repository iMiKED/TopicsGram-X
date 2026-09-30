package org.thunderdog.challegram.data

import org.junit.Assert.*
import org.junit.Test

class ForumRailLayoutTest {
  @Test fun `phone and tablet keep rail visible and content usable`() {
    for (width in intArrayOf(320, 360, 412, 600, 840)) {
      val rail = ForumRailLayout.widthDp(width.toFloat())
      assertTrue(rail >= 48)
      assertTrue(width - rail >= 264)
      assertEquals(rail, ForumRailLayout.occupied(rail, 1f))
      assertEquals(0, ForumRailLayout.occupied(rail, 0f))
    }
  }
  @Test fun `rtl mirrors rail but never claims content touches`() {
    assertTrue(ForumRailLayout.hitRail(360, 64, 32f, false))
    assertFalse(ForumRailLayout.hitRail(360, 64, 64f, false))
    assertTrue(ForumRailLayout.hitRail(360, 64, 328f, true))
    assertFalse(ForumRailLayout.hitRail(360, 64, 295f, true))
    assertFalse(ForumRailLayout.hitRail(360, 0, 0f, false))
    assertFalse(ForumRailLayout.hitRail(360, 64, -1f, false))
    assertFalse(ForumRailLayout.hitRail(360, 64, 360f, true))
  }
  @Test fun `gesture coordinates follow the measured animation boundary`() {
    for (step in 0..10) {
      val occupied = ForumRailLayout.occupied(64, step / 10f)
      assertEquals(0f, ForumRailLayout.contentX(occupied, occupied.toFloat(), false), 0f)
      assertEquals(24f, ForumRailLayout.contentX(occupied, 24f, true), 0f)
    }
    assertEquals(0, ForumRailLayout.occupied(64, -1f))
    assertEquals(64, ForumRailLayout.occupied(64, 2f))
  }
}
