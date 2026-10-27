package org.thunderdog.challegram.telegram

import me.vkryl.annotation.MadeByLLM
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.thunderdog.challegram.data.ForumTabsState

@MadeByLLM("gpt-6-astra")
class ForumUpstreamMigrationCheck {
  @Test
  fun recentChatsKeepLongIdsWithoutMutatingInput() {
    val previous = longArrayOf(Long.MIN_VALUE, 0, Long.MAX_VALUE, Long.MIN_VALUE)
    val original = previous.copyOf()
    val updated = ForumTabsStateMru.touch(previous, Long.MAX_VALUE)
    assertArrayEquals(longArrayOf(Long.MAX_VALUE, Long.MIN_VALUE), updated)
    assertArrayEquals(original, previous)
  }

  @Test
  fun recentChatsStayBoundedAndRejectZeroId() {
    val previous = LongArray(100) {
      (it + 1).toLong()
    }
    val updated = ForumTabsStateMru.touch(previous, 101L)
    assertEquals(ForumTabsStateMru.MAX_CHATS, updated.size)
    assertEquals(101L, updated[0])
    assertEquals(63L, updated.last())
    assertThrows(IllegalArgumentException::class.java) {
      ForumTabsStateMru.touch(previous, 0L)
    }
  }

  @Test
  fun placementCycleRetainsTheTopicAndAllSelection() {
    for (topicId in intArrayOf(0, 73)) {
      var state = ForumTabsState.defaultState().withSelectedTopic(topicId)
      val cycle = intArrayOf(
        ForumTabsState.START,
        ForumTabsState.BOTTOM,
        ForumTabsState.START,
        ForumTabsState.TOP
      )
      for (placement in cycle) {
        state = state.cyclePlacement()
        assertEquals(placement, state.placement)
        assertEquals(topicId.toLong(), state.selectedTopicId!!.toLong())
        assertArrayEquals(state.encode(), ForumTabsState.decode(state.encode()).encode())
      }
    }
  }

  @Test
  fun malformedStoredStateReturnsToDefault() {
    val malformed = arrayOf(
      intArrayOf(),
      intArrayOf(2, 0, 2, 73),
      intArrayOf(1, 0, 0, 73),
      intArrayOf(1, 0, 2, -2)
    )
    for (encoded in malformed) {
      assertArrayEquals(
        ForumTabsState.defaultState().encode(),
        ForumTabsState.decode(encoded).encode()
      )
    }
  }
}
