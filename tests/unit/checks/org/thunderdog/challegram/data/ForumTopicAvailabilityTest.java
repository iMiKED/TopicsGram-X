package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.*;

/** Local-only regression tests with the real Java model and generated TD API. */
@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-checks-package")
public class ForumTopicAvailabilityTest {
  @Test public void missingAndForbiddenTopicsAreUnavailable () {
    assertTrue(ForumTopicContext.isUnavailable(new TdApi.Error(404, "Not found")));
    assertTrue(ForumTopicContext.isUnavailable(new TdApi.Error(403, "Access denied")));
  }

  @Test public void explicitDeletedAndNotFoundErrorsAreUnavailable () {
    assertTrue(ForumTopicContext.isUnavailable(new TdApi.Error(400, "TOPIC_DELETED")));
    assertTrue(ForumTopicContext.isUnavailable(new TdApi.Error(400, "TOPIC_NOT_FOUND")));
  }

  @Test public void networkRateLimitAndOtherErrorsRemainRetryable () {
    assertFalse(ForumTopicContext.isUnavailable(null));
    assertFalse(ForumTopicContext.isUnavailable(new TdApi.Error(408, "Request timed out")));
    assertFalse(ForumTopicContext.isUnavailable(new TdApi.Error(429, "Too many requests")));
    assertFalse(ForumTopicContext.isUnavailable(new TdApi.Error(500, "Internal Server Error")));
    assertFalse(ForumTopicContext.isUnavailable(new TdApi.Error(400, "An unrelated TOPIC_DELETED substring")));
    assertFalse(ForumTopicContext.isUnavailable(new TdApi.Error(400, null)));
  }

  @Test public void errorEndsLoadingAndCanBeReplacedByANewObservation () {
    ForumTopicContext context = new ForumTopicContext(700, 31);
    assertTrue(context.isLoading());
    assertTrue(context.update(null, new TdApi.Error(404, "Not found")));
    assertFalse(context.isLoading());
    assertTrue(ForumTopicContext.isUnavailable(context.error()));
    assertFalse(context.canSend(new TdApi.ChatMemberStatusCreator(), true));
    assertTrue(context.update(null, new TdApi.Error(500, "Retry later")));
    assertFalse(ForumTopicContext.isUnavailable(context.error()));
    assertFalse(context.isLoading());
    assertTrue(context.update(null, null));
    assertTrue(context.isLoading());
  }
}