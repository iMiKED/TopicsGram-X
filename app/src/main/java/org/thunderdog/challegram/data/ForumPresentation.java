package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;

/** Pure common-stream rules: display identity, composer identity and read actions must agree. */
public final class ForumPresentation {
  private ForumPresentation () { }

  public static boolean showTopicButton (TdApi.MessageTopic message, TdApi.MessageTopic viewed, boolean thread, boolean scheduled, boolean preview) {
    return ForumHistory.isForum(message) && viewed == null && !thread && !scheduled && !preview;
  }

  public static TdApi.MessageTopic composerTopic (long chatId, TdApi.MessageTopic viewed, TdApi.Message reply) {
    return ForumHistory.outgoingTopic(viewed, ForumNavigation.forumTopic(chatId, reply));
  }

  public static boolean isMuted (TdApi.ChatNotificationSettings settings, boolean groupMuted) {
    return settings == null || settings.useDefaultMuteFor ? groupMuted : settings.muteFor > 0;
  }

  public static TdApi.Function<TdApi.Ok> readMentions (long chatId, TdApi.MessageTopic topic) {
    return ForumHistory.isForum(topic) ? new TdApi.ReadAllForumTopicMentions(chatId, ((TdApi.MessageTopicForum) topic).forumTopicId) : new TdApi.ReadAllChatMentions(chatId);
  }

  public static TdApi.Function<TdApi.Ok> readReactions (long chatId, TdApi.MessageTopic topic) {
    return ForumHistory.isForum(topic) ? new TdApi.ReadAllForumTopicReactions(chatId, ((TdApi.MessageTopicForum) topic).forumTopicId) : new TdApi.ReadAllChatReactions(chatId);
  }

  public static TdApi.SearchChatMessages unreadPollVotes (long chatId, int topicId) {
    return new TdApi.SearchChatMessages(chatId, new TdApi.MessageTopicForum(topicId), "", null, 0, 0, 1, new TdApi.SearchMessagesFilterUnreadPollVote());
  }
}
