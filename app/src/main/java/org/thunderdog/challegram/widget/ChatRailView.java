package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.ChatListListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibChatList;
import org.thunderdog.challegram.telegram.TdlibChatListSlice;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.lambda.RunnableLong;

/** A paged projection of the existing account/folder list, not a second chat cache. */
public final class ChatRailView extends FrameLayout implements ChatListListener, NotificationSettingsListener {
  private final Tdlib tdlib;
  private final TdlibChatListSlice slice;
  private final RecyclerView list;
  private final LinearLayoutManager layout;
  private final RailAdapter adapter = new RailAdapter();
  private final ArrayList<Long> chats = new ArrayList<>();
  private final RunnableLong openChat;
  private boolean destroyed, loading, initialized;
  private long selectedChat;
  private int restorePosition = -1, restoreOffset;

  public ChatRailView (Context context, Tdlib tdlib, TdApi.ChatList source, RunnableLong openChat) {
    super(context);
    this.tdlib = tdlib;
    this.openChat = openChat;
    slice = tdlib.chatList(source).slice(null);
    setBackgroundColor(Theme.fillingColor());
    list = new RecyclerView(context);
    layout = new LinearLayoutManager(context);
    list.setLayoutManager(layout);
    list.setAdapter(adapter);
    list.setItemAnimator(null);
    list.setClipToPadding(false);
    list.setVerticalScrollBarEnabled(false);
    list.setContentDescription(Lang.getString(R.string.ForumRailChats));
    addView(list, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    updateTheme();
    list.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override public void onScrolled (@NonNull RecyclerView view, int dx, int dy) { loadMore(); }
    });
    tdlib.listeners().subscribeToSettingsUpdates(this);
    slice.initializeList(this, entries -> {
      ArrayList<Long> ids = new ArrayList<>(entries.size());
      for (TdlibChatListSlice.Entry entry : entries) ids.add(entry.chat.id);
      dispatch(() -> {
        int start = chats.size();
        for (Long id : ids) if (!chats.contains(id)) chats.add(id);
        adapter.notifyItemRangeInserted(start, chats.size() - start);
        applyScrollPosition();
        loadMore();
      });
    }, 30, () -> dispatch(() -> { initialized = true; applyScrollPosition(); loadMore(); }));
  }

  public void setInsets (int top, int bottom) {
    list.setPadding(0, top + HeaderView.getSize(false), 0, bottom);
  }

  public void updateTheme () {
    setBackgroundColor(Theme.fillingColor());
    adapter.notifyItemRangeChanged(0, chats.size());
  }

  public void setSelectedChat (long chatId) {
    if (selectedChat == chatId) return;
    long old = selectedChat;
    selectedChat = chatId;
    int oldIndex = chats.indexOf(old), newIndex = chats.indexOf(chatId);
    if (oldIndex >= 0) adapter.notifyItemChanged(oldIndex);
    if (newIndex >= 0) adapter.notifyItemChanged(newIndex);
    // Deliberately do not insert or bringToTop a deep-linked chat outside this folder.
  }

  private void dispatch (Runnable action) {
    tdlib.ui().post(() -> { if (!destroyed) action.run(); });
  }

  private void loadMore () {
    if (destroyed || !initialized || loading || !slice.canLoad() || restorePosition < chats.size() && layout.findLastVisibleItemPosition() < chats.size() - 6) return;
    loading = true;
    int previousSize = chats.size();
    slice.loadMore(30, () -> dispatch(() -> {
      loading = false;
      applyScrollPosition();
      if (chats.size() > previousSize) loadMore(); // A failed/empty page must not spin on reconnect.
    }));
  }

  public int scrollPosition () { return restorePosition >= 0 ? restorePosition : Math.max(0, layout.findFirstVisibleItemPosition()); }
  public int scrollOffset () {
    if (restorePosition >= 0) return restoreOffset;
    View first = layout.findViewByPosition(layout.findFirstVisibleItemPosition());
    return first != null ? layout.getDecoratedTop(first) - list.getPaddingTop() : 0;
  }
  public void restoreScrollPosition (int position, int offset) {
    restorePosition = Math.max(0, position); restoreOffset = offset;
    applyScrollPosition();
    loadMore();
  }
  private void applyScrollPosition () {
    if (restorePosition >= 0 && !chats.isEmpty() && (restorePosition < chats.size() || slice.isEndReached())) {
      layout.scrollToPositionWithOffset(Math.min(restorePosition, chats.size() - 1), restoreOffset);
      restorePosition = -1;
    }
  }

  private void changed (long chatId) {
    int index = chats.indexOf(chatId);
    if (index >= 0) adapter.notifyItemChanged(index);
  }

  @Override public void onChatAdded (TdlibChatList source, TdApi.Chat chat, int at, Tdlib.ChatChange change) {
    long id = chat.id;
    dispatch(() -> {
      if (chats.contains(id)) { changed(id); return; }
      int index = Math.min(at, chats.size());
      chats.add(index, id); adapter.notifyItemInserted(index);
    });
  }
  @Override public void onChatRemoved (TdlibChatList source, TdApi.Chat chat, int from, Tdlib.ChatChange change) {
    long id = chat.id;
    dispatch(() -> { int i = chats.indexOf(id); if (i >= 0) { chats.remove(i); adapter.notifyItemRemoved(i); } });
  }
  @Override public void onChatMoved (TdlibChatList source, TdApi.Chat chat, int from, int to, Tdlib.ChatChange change) {
    long id = chat.id;
    dispatch(() -> {
      int old = chats.indexOf(id);
      if (old >= 0) { chats.remove(old); int target = Math.min(to, chats.size()); chats.add(target, id); adapter.notifyItemMoved(old, target); }
    });
  }
  @Override public void onChatChanged (TdlibChatList source, TdApi.Chat chat, int at, Tdlib.ChatChange change) { long id = chat.id; dispatch(() -> changed(id)); }
  @Override public void onChatListItemChanged (TdlibChatList source, TdApi.Chat chat, int type) { long id = chat.id; dispatch(() -> changed(id)); }
  @Override public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) { dispatch(() -> changed(chatId)); }
  @Override public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) { dispatch(() -> adapter.notifyItemRangeChanged(0, chats.size())); }

  public void destroy () {
    if (destroyed) return;
    destroyed = true;
    slice.performDestroy();
    tdlib.listeners().unsubscribeFromSettingsUpdates(this);
    list.setAdapter(null);
    for (RailRow row : adapter.rows) row.destroy();
    adapter.rows.clear();
    chats.clear();
  }

  private final class RailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    final List<RailRow> rows = new ArrayList<>();
    RailAdapter () { setHasStableIds(true); }
    @Override public long getItemId (int position) { return chats.get(position); }
    @Override public int getItemCount () { return chats.size(); }
    @Override public RecyclerView.ViewHolder onCreateViewHolder (ViewGroup parent, int type) {
      RailRow row = new RailRow(parent.getContext());
      rows.add(row);
      row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(64)));
      return new RecyclerView.ViewHolder(row) { };
    }
    @Override public void onBindViewHolder (RecyclerView.ViewHolder holder, int position) { ((RailRow) holder.itemView).bind(chats.get(position)); }
    @Override public void onViewRecycled (RecyclerView.ViewHolder holder) { ((RailRow) holder.itemView).clear(); }
  }

  private final class RailRow extends View {
    final AvatarReceiver avatar = new AvatarReceiver(this);
    final Counter counter = new Counter.Builder().callback(this).outlineColor(ColorId.filling).build();
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    long chatId;
    RailRow (Context context) {
      super(context);
      setFocusable(true);
      RippleSupport.setTransparentSelector(this);
      setOnClickListener(v -> { if (chatId != 0) openChat.runWithLong(chatId); });
      setOnLongClickListener(v -> { if (android.os.Build.VERSION.SDK_INT >= 26) { setTooltipText(tdlib.chatTitle(chatId)); return false; } return false; });
      avatar.detach();
    }
    void bind (long id) {
      chatId = id;
      TdApi.Chat chat = tdlib.chat(id);
      avatar.requestChat(tdlib, id, AvatarReceiver.Options.SHOW_ONLINE);
      boolean muted = !tdlib.chatNotificationsEnabled(id);
      counter.setCount(chat == null ? 0 : chat.unreadCount > 0 ? chat.unreadCount : chat.isMarkedAsUnread ? Tdlib.CHAT_MARKED_AS_UNREAD : 0, muted, false);
      setSelected(id == selectedChat);
      setContentDescription((chat != null ? chat.title : "") + (muted ? ", " + Lang.getString(R.string.ForumRailMuted) : "") +
        (chat != null && chat.unreadCount > 0 ? ", " + Lang.getString(R.string.ForumRailUnread, chat.unreadCount) : ""));
      invalidate();
    }
    void clear () { chatId = 0; avatar.clear(); }
    void destroy () { avatar.destroy(); }
    @Override protected void onAttachedToWindow () { super.onAttachedToWindow(); avatar.attach(); }
    @Override protected void onDetachedFromWindow () { avatar.detach(); super.onDetachedFromWindow(); }
    @Override public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) { super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Button"); info.setSelected(isSelected()); }
    @Override protected void onDraw (Canvas canvas) {
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      int radius = Math.min(Screen.dp(23), getWidth() / 2 - Screen.dp(5));
      if (isSelected()) {
        paint.setColor(Theme.getColor(ColorId.iconActive));
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Screen.dp(2));
        canvas.drawCircle(cx, cy, radius + Screen.dp(3), paint); paint.setStyle(Paint.Style.FILL);
      }
      avatar.setBounds((int) cx - radius, (int) cy - radius, (int) cx + radius, (int) cy + radius);
      if (avatar.needPlaceholder()) avatar.drawPlaceholder(canvas);
      avatar.draw(canvas);
      counter.draw(canvas, cx + radius, cy + radius - Screen.dp(3), Gravity.RIGHT, 1f);
    }
  }
}
