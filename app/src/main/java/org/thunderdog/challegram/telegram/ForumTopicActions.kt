package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.telegram.TdlibForumTopicManager.Key

/** Server-authoritative operations. Permission checks and confirmation dialogs belong to the UI. */
class ForumTopicActions internal constructor(private val store: ForumTopicStore) {
  fun interface Callback<T : TdApi.Object> { fun onResult(value: T?, error: TdApi.Error?) }

  private fun <T : TdApi.Object> send(request: TdApi.Function<T>, chatId: Long, key: Key?, constructor: Int, callback: Callback<T>, changesState: Boolean = true) {
    store.perform(request, chatId, key, constructor, changesState) { value, error ->
      @Suppress("UNCHECKED_CAST")
      callback.onResult(value as T?, error)
    }
  }

  fun create(chatId: Long, name: String, isNameImplicit: Boolean, icon: TdApi.ForumTopicIcon, callback: Callback<TdApi.ForumTopicInfo>) =
    send(TdApi.CreateForumTopic(chatId, name, isNameImplicit, icon), chatId, null, TdApi.ForumTopicInfo.CONSTRUCTOR, callback)

  fun edit(key: Key, name: String, editIconCustomEmoji: Boolean, customEmojiId: Long, callback: Callback<TdApi.Ok>) =
    send(TdApi.EditForumTopic(key.chatId, key.forumTopicId, name, editIconCustomEmoji, customEmojiId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun setClosed(key: Key, closed: Boolean, callback: Callback<TdApi.Ok>) =
    send(TdApi.ToggleForumTopicIsClosed(key.chatId, key.forumTopicId, closed), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun setGeneralHidden(chatId: Long, hidden: Boolean, callback: Callback<TdApi.Ok>) =
    send(TdApi.ToggleGeneralForumTopicIsHidden(chatId, hidden), chatId, Key(chatId, 1), TdApi.Ok.CONSTRUCTOR, callback)

  fun setPinned(key: Key, pinned: Boolean, callback: Callback<TdApi.Ok>) =
    send(TdApi.ToggleForumTopicIsPinned(key.chatId, key.forumTopicId, pinned), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun setPinnedOrder(chatId: Long, topicIds: IntArray, callback: Callback<TdApi.Ok>) =
    send(TdApi.SetPinnedForumTopics(chatId, topicIds.copyOf()), chatId, null, TdApi.Ok.CONSTRUCTOR, callback)

  // TDLib decides whether the topic disappears or only its history is cleared (notably General).
  fun delete(key: Key, callback: Callback<TdApi.Ok>) =
    send(TdApi.DeleteForumTopic(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun setNotifications(key: Key, settings: TdApi.ChatNotificationSettings, callback: Callback<TdApi.Ok>) =
    send(TdApi.SetForumTopicNotificationSettings(key.chatId, key.forumTopicId, settings), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun readAllMentions(key: Key, callback: Callback<TdApi.Ok>) =
    send(TdApi.ReadAllForumTopicMentions(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun readAllReactions(key: Key, callback: Callback<TdApi.Ok>) =
    send(TdApi.ReadAllForumTopicReactions(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun readAllPollVotes(key: Key, callback: Callback<TdApi.Ok>) =
    send(TdApi.ReadAllForumTopicPollVotes(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun unpinAllMessages(key: Key, callback: Callback<TdApi.Ok>) =
    send(TdApi.UnpinAllForumTopicMessages(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun getLink(key: Key, callback: Callback<TdApi.MessageLink>) =
    send(TdApi.GetForumTopicLink(key.chatId, key.forumTopicId), key.chatId, key, TdApi.MessageLink.CONSTRUCTOR, callback, changesState = false)

  fun setViewAsTopics(chatId: Long, viewAsTopics: Boolean, callback: Callback<TdApi.Ok>) =
    send(TdApi.ToggleChatViewAsTopics(chatId, viewAsTopics), chatId, null, TdApi.Ok.CONSTRUCTOR, callback)
}
