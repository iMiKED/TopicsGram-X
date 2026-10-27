package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-checks-package")
public class ForumNotificationReadScopeTest {
  private static final long CHAT_ID = 42;

  private static TdApi.NotificationType message (TdApi.MessageTopic topicId) {
    TdApi.Message message = new TdApi.Message();
    message.topicId = topicId;
    return new TdApi.NotificationTypeNewMessage(message, true);
  }

  private static TdApi.NotificationType forumMessage (int topicId) {
    return message(new TdApi.MessageTopicForum(topicId));
  }

  private static int[] topics (TdApi.NotificationType... notifications) {
    return ForumNotificationReadScope.topicIds(notifications.length, Arrays.asList(notifications));
  }

  private static void assertChatWide (int[] topicIds) {
    List<TdApi.Function<TdApi.Ok>> requests = ForumNotificationReadScope.requests(CHAT_ID, topicIds);
    assertEquals(1, requests.size());
    assertTrue(requests.get(0) instanceof TdApi.ReadAllChatMentions);
    assertEquals(CHAT_ID, ((TdApi.ReadAllChatMentions) requests.get(0)).chatId);
  }

  @Test public void mixedTopicsReadEveryRepresentedTopicNotJustLatest () {
    int[] topicIds = topics(forumMessage(7), forumMessage(9), forumMessage(7));
    assertArrayEquals(new int[] {7, 9}, topicIds);
    List<TdApi.Function<TdApi.Ok>> requests = ForumNotificationReadScope.requests(CHAT_ID, topicIds);
    assertEquals(2, requests.size());
    for (int i = 0; i < requests.size(); i++) {
      assertTrue(requests.get(i) instanceof TdApi.ReadAllForumTopicMentions);
      TdApi.ReadAllForumTopicMentions request = (TdApi.ReadAllForumTopicMentions) requests.get(i);
      assertEquals(CHAT_ID, request.chatId);
      assertEquals(topicIds[i], request.forumTopicId);
    }
  }

  @Test public void generalIsAnExplicitTopicAndRepeatedTopicsNeedOnlyOneRequest () {
    int[] topicIds = topics(forumMessage(1), forumMessage(1));
    assertArrayEquals(new int[] {1}, topicIds);
    List<TdApi.Function<TdApi.Ok>> requests = ForumNotificationReadScope.requests(CHAT_ID, topicIds);
    assertEquals(1, requests.size());
    assertEquals(1, ((TdApi.ReadAllForumTopicMentions) requests.get(0)).forumTopicId);
  }

  @Test public void serializedScopeIsDeduplicatedWithoutChangingItsInput () {
    int[] topicIds = {9, 7, 9};
    List<TdApi.Function<TdApi.Ok>> requests = ForumNotificationReadScope.requests(CHAT_ID, topicIds);
    assertEquals(2, requests.size());
    assertEquals(9, ((TdApi.ReadAllForumTopicMentions) requests.get(0)).forumTopicId);
    assertEquals(7, ((TdApi.ReadAllForumTopicMentions) requests.get(1)).forumTopicId);
    assertArrayEquals(new int[] {9, 7, 9}, topicIds);
  }

  @Test public void nonForumMentionsRetainTheChatWideRead () {
    int[] topicIds = topics(message(null));
    assertNull(topicIds);
    assertChatWide(topicIds);
  }

  @Test public void pushWithoutTopicNeverBorrowsTheLatestMessagesTopic () {
    TdApi.NotificationTypeNewPushMessage push = new TdApi.NotificationTypeNewPushMessage();
    push.messageId = 100;
    assertNull(topics(push));
    assertNull(topics(push, forumMessage(9)));
    assertNull(topics(forumMessage(9), push));
    assertChatWide(topics(push, forumMessage(9)));
  }

  @Test public void missingMetadataAndUnknownTypesUseTheCompatibleFallback () {
    assertChatWide(topics(new TdApi.NotificationTypeNewMessage()));
    assertChatWide(topics(new TdApi.NotificationTypeNewSecretChat()));
    assertChatWide(topics(forumMessage(9), null));
    assertChatWide(topics(forumMessage(9), message(null)));
  }

  @Test public void incompleteCachedGroupCannotDismissUnrepresentedTopics () {
    int[] topicIds = ForumNotificationReadScope.topicIds(3, Arrays.asList(forumMessage(7), forumMessage(9)));
    assertNull(topicIds);
    assertChatWide(topicIds);
  }

  @Test public void legacyIntentsAndEmptySnapshotsDoNotDependOnAnInMemoryGroup () {
    assertChatWide(null);
    assertChatWide(new int[0]);
    assertNull(ForumNotificationReadScope.topicIds(0, Collections.emptyList()));
    assertNull(ForumNotificationReadScope.topicIds(1, null));
  }

  @Test public void invalidTopicIdentityFallsBackForTheWholeGroupNotOnlyTheValidSubset () {
    for (int invalid : new int[] {0, -1}) {
      assertNull(topics(forumMessage(9), forumMessage(invalid)));
      assertChatWide(new int[] {9, invalid});
    }
  }

  @Test public void groupDismissalWaitsForEverySuccessfulRead () {
    ForumNotificationReadScope.Completion completion = new ForumNotificationReadScope.Completion(3);
    assertFalse(completion.onResult(true));
    assertFalse(completion.onResult(true));
    assertTrue(completion.onResult(true));
    assertFalse(completion.onResult(true));
  }

  @Test public void anyFailedTopicPreventsGroupDismissalRegardlessOfCallbackOrder () {
    for (int failedIndex = 0; failedIndex < 3; failedIndex++) {
      ForumNotificationReadScope.Completion completion = new ForumNotificationReadScope.Completion(3);
      for (int index = 0; index < 3; index++) {
        assertFalse(completion.onResult(index != failedIndex));
      }
    }
  }

  @Test public void failedChatWideFallbackAlsoKeepsTheNotification () {
    ForumNotificationReadScope.Completion failure = new ForumNotificationReadScope.Completion(1);
    assertFalse(failure.onResult(false));
    ForumNotificationReadScope.Completion success = new ForumNotificationReadScope.Completion(1);
    assertTrue(success.onResult(true));
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyRequestSetCannotBeTreatedAsSuccessful () {
    new ForumNotificationReadScope.Completion(0);
  }

  private static TdApi.Message ordinaryMessage (long id, int topic) {
    TdApi.Message message = new TdApi.Message();
    message.id = id;
    message.chatId = CHAT_ID;
    message.topicId = topic > 0 ? new TdApi.MessageTopicForum(topic) : null;
    return message;
  }

  @Test public void ordinaryMessagesAreSplitByTopicBeforeViewing () {
    long[] ids = {101, 102, 103};
    List<TdApi.ViewMessages> requests = ForumNotificationReadScope.messageRequests(CHAT_ID, ids,
      new TdApi.Message[] {ordinaryMessage(101, 7), ordinaryMessage(102, 9), ordinaryMessage(103, 7)});
    assertEquals(2, requests.size());
    assertArrayEquals(new long[] {101, 103}, requests.get(0).messageIds);
    assertArrayEquals(new long[] {102}, requests.get(1).messageIds);
    for (TdApi.ViewMessages request : requests) {
      assertEquals(CHAT_ID, request.chatId);
      assertTrue(request.forceRead);
      assertTrue(request.source instanceof TdApi.MessageSourceForumTopicHistory);
    }
    assertArrayEquals(new long[] {101, 102, 103}, ids);
  }

  @Test public void ordinaryGeneralAndNonForumMessagesKeepDifferentSources () {
    List<TdApi.ViewMessages> requests = ForumNotificationReadScope.messageRequests(CHAT_ID, new long[] {1, 2},
      new TdApi.Message[] {ordinaryMessage(1, 1), ordinaryMessage(2, 0)});
    assertEquals(2, requests.size());
    assertTrue(requests.get(0).source instanceof TdApi.MessageSourceForumTopicHistory);
    assertTrue(requests.get(1).source instanceof TdApi.MessageSourceNotification);
  }

  @Test public void ordinaryReplyReadOnlyContainsItsTargetMessage () {
    List<TdApi.ViewMessages> requests = ForumNotificationReadScope.messageRequests(CHAT_ID, new long[] {103},
      new TdApi.Message[] {ordinaryMessage(103, 9)});
    assertEquals(1, requests.size());
    assertArrayEquals(new long[] {103}, requests.get(0).messageIds);
  }

  @Test public void ordinaryDuplicateMessageIdsAreNotSentTwice () {
    List<TdApi.ViewMessages> requests = ForumNotificationReadScope.messageRequests(CHAT_ID, new long[] {101, 101},
      new TdApi.Message[] {ordinaryMessage(101, 7), ordinaryMessage(101, 7)});
    assertEquals(1, requests.size());
    assertArrayEquals(new long[] {101}, requests.get(0).messageIds);
  }

  @Test public void ordinaryMissingMessagesNeverFallBackToChatWideRead () {
    assertNull(ForumNotificationReadScope.messageRequests(CHAT_ID, new long[] {101}, new TdApi.Message[] {null}));
    assertNull(ForumNotificationReadScope.messageRequests(CHAT_ID, new long[] {101}, null));
    assertNull(ForumNotificationReadScope.messageRequests(CHAT_ID, new long[] {101}, new TdApi.Message[0]));
    assertNull(ForumNotificationReadScope.messageRequests(CHAT_ID, null, new TdApi.Message[0]));
    assertNull(ForumNotificationReadScope.messageRequests(CHAT_ID, new long[0], new TdApi.Message[0]));
  }

  @Test public void ordinaryMismatchedMessageOrChatFailsTheWholeScope () {
    TdApi.Message wrongChat = ordinaryMessage(102, 9);
    wrongChat.chatId++;
    assertNull(ForumNotificationReadScope.messageRequests(CHAT_ID, new long[] {101, 102},
      new TdApi.Message[] {ordinaryMessage(101, 7), wrongChat}));
    assertNull(ForumNotificationReadScope.messageRequests(CHAT_ID, new long[] {101},
      new TdApi.Message[] {ordinaryMessage(102, 7)}));
  }

  @Test public void ordinaryInvalidTopicFailsTheWholeScope () {
    for (int invalid : new int[] {0, -1}) {
      TdApi.Message message = ordinaryMessage(101, 7);
      message.topicId = new TdApi.MessageTopicForum(invalid);
      assertNull(ForumNotificationReadScope.messageRequests(CHAT_ID, new long[] {101}, new TdApi.Message[] {message}));
    }
  }
}