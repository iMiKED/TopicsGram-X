package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.ForumTopicView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.CleanupStartupDelegate;
import org.thunderdog.challegram.telegram.ForumTopicStore;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.Collections;
import java.util.List;

/** Owns a cancellable list session; no forum identifiers are encoded as generic threads. */
public final class ForumTopicsController extends RecyclerViewController<ForumTopicsController.Arguments> implements ChatListener, CleanupStartupDelegate {
  public static final class Arguments {
    public final long chatId;
    public final TdApi.ChatList chatList;
    public Arguments (long chatId, TdApi.ChatList chatList) { this.chatId = chatId; this.chatList = chatList; }
  }

  private ForumTopicStore.ListSession session;
  private ForumTopicStore.Snapshot snapshot;
  private TopicAdapter adapter;
  private String query = "";
  private Object sessionEpoch;
  private volatile Object lifecycleEpoch = new Object();
  private boolean subscribed, restoreSearch;
  private int restorePosition = -1, restoreOffset;

  public ForumTopicsController (Context context, Tdlib tdlib) { super(context, tdlib); }
  @Override public int getId () { return R.id.controller_forumTopics; }
  @Override public long getChatId () { return getArgumentsStrict().chatId; }
  @Override public CharSequence getName () { return tdlib.chatTitle(getChatId()); }
  @Override protected int getRecyclerBackground () { return ColorId.filling; }
  @Override protected int getMenuId () { return R.id.menu_forumTopics; }
  @Override protected int getSearchMenuId () { return R.id.menu_clear; }
  @Override protected int getSearchHint () { return R.string.ForumSearchTopics; }

  @Override protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new TopicAdapter();
    recyclerView.setAdapter(adapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override public void onScrolled (@NonNull RecyclerView view, int dx, int dy) { loadMoreIfNeeded(); }
    });
    tdlib.listeners().subscribeToChatUpdates(getChatId(), this);
    tdlib.listeners().addCleanupListener(this);
    tdlib.openChat(getChatId(), this);
    subscribed = true;
    openSession();
  }

  private void closeSession () {
    sessionEpoch = new Object();
    if (session != null) session.close();
    session = null;
  }

  private void openSession () {
    closeSession();
    Object epoch = sessionEpoch;
    snapshot = tdlib.topics().cachedList(getChatId(), query);
    adapter.update(snapshot);
    session = tdlib.topics().openList(getChatId(), query, value -> {
      if (isDestroyed() || sessionEpoch != epoch || !query.equals(value.key.query)) return;
      snapshot = value;
      adapter.update(value);
      if (restorePosition >= 0 && !value.topics.isEmpty() && (restorePosition < value.topics.size() || value.endReached)) {
        ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(Math.min(restorePosition, value.topics.size()-1), restoreOffset);
        restorePosition = -1;
      }
      getRecyclerView().post(this::loadMoreIfNeeded);
    });
  }

  private void loadMoreIfNeeded () {
    if (session == null || snapshot == null || snapshot.error != null || snapshot.endReached || snapshot.loadingInitial || snapshot.loadingMore || snapshot.refreshing) return;
    LinearLayoutManager manager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
    if (manager != null && (restorePosition >= adapter.topics.size() || manager.findLastVisibleItemPosition() >= adapter.topics.size()-6)) session.loadMore();
  }

  private void setQuery (String value) {
    value = value.trim();
    if (query.equals(value)) return;
    query = value;
    restorePosition = -1;
    snapshot = null;
    adapter.update(null);
    getRecyclerView().scrollToPosition(0);
    if (session != null) session.setQuery(query);
  }

  @Override protected void onSearchInputChanged (String query) { setQuery(query); }
  @Override protected void onLeaveSearchMode () { setQuery(""); }
  @Override public void onFocus () {
    super.onFocus();
    if (restoreSearch) {
      restoreSearch = false;
      String savedQuery = query;
      openSearchMode();
      setSearchInput(savedQuery);
    }
    if (adapter != null) adapter.notifyDataSetChanged(); // Local drafts may have changed while a topic was open.
  }

  @Override public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_forumTopics) {
      header.addSearchButton(menu, this);
      header.addButton(menu, R.id.menu_btn_retry, R.drawable.baseline_sync_24, getHeaderIconColorId(), this, Screen.dp(49));
    } else if (id == R.id.menu_clear) header.addClearButton(menu, this);
  }
  @Override public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) openSearchMode();
    else if (id == R.id.menu_btn_clear) clearSearchInput();
    else if (id == R.id.menu_btn_retry && session != null) session.refresh();
  }

  @Override public void onChatTitleChanged (long chatId, String title) {
    runOnUiThreadOptional(() -> { if (headerView != null) headerView.updateTextTitle(getId(), getName()); });
  }

  @Override public void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1);
    if (adapter != null) adapter.notifyDataSetChanged();
  }

  @Override public void onScrollToTopRequested () {
    if (getRecyclerView() != null) getRecyclerView().smoothScrollToPosition(0);
  }

  @Override public void onPerformUserCleanup () {
    Object epoch = lifecycleEpoch = new Object();
    tdlib.ui().post(() -> {
      if (isDestroyed() || lifecycleEpoch != epoch) return;
      closeSession(); snapshot = null;
      if (adapter != null) adapter.update(null);
    });
  }
  @Override public void onPerformRestart () { onPerformUserCleanup(); }
  @Override public void onPerformStartup (boolean afterRestart) {
    Object epoch = lifecycleEpoch = new Object();
    tdlib.ui().post(() -> {
      if (!isDestroyed() && lifecycleEpoch == epoch && adapter != null) openSession();
    });
  }

  @Override public void destroy () {
    closeSession();
    if (subscribed) {
      tdlib.listeners().unsubscribeFromChatUpdates(getChatId(), this);
      tdlib.listeners().removeCleanupListener(this);
      tdlib.closeChat(getChatId(), this, false);
      subscribed = false;
    }
    if (getRecyclerView() != null) getRecyclerView().setAdapter(null);
    super.destroy();
  }

  @Override public boolean saveInstanceState (Bundle out, String prefix) {
    super.saveInstanceState(out, prefix);
    out.putLong(prefix+"forum_chat", getChatId());
    out.putString(prefix+"forum_list", getArgumentsStrict().chatList != null ? TD.makeChatListKey(getArgumentsStrict().chatList) : "");
    out.putString(prefix+"forum_query", query);
    return true;
  }
  @Override public boolean restoreInstanceState (Bundle in, String prefix) {
    long chatId = in.getLong(prefix+"forum_chat");
    if (chatId == 0 || tdlib.chatSync(chatId) == null || !tdlib.isForum(chatId)) return false;
    setArguments(new Arguments(chatId, TD.chatListFromKey(in.getString(prefix+"forum_list"))));
    query = in.getString(prefix+"forum_query", "");
    restoreSearch = !query.isEmpty();
    restorePosition = in.getInt(prefix+"base_scroll_position", -1);
    restoreOffset = in.getInt(prefix+"base_scroll_offset", 0);
    super.restoreInstanceState(in, prefix);
    return true;
  }

  private final class TopicAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<TdApi.ForumTopic> topics = Collections.emptyList();
    TopicAdapter () { setHasStableIds(true); }
    @Override public long getItemId (int position) { return position < topics.size() ? topics.get(position).info.forumTopicId : Long.MIN_VALUE; }
    @Override public int getItemCount () { return topics.size()+1; }
    @Override public int getItemViewType (int position) { return position < topics.size() ? 0 : 1; }

    void update (ForumTopicStore.Snapshot value) {
      List<TdApi.ForumTopic> old = topics;
      List<TdApi.ForumTopic> next = value != null ? value.topics : Collections.emptyList();
      DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
        @Override public int getOldListSize () { return old.size()+1; }
        @Override public int getNewListSize () { return next.size()+1; }
        @Override public boolean areItemsTheSame (int a, int b) {
          if (a == old.size() || b == next.size()) return a == old.size() && b == next.size();
          return old.get(a).info.forumTopicId == next.get(b).info.forumTopicId;
        }
        @Override public boolean areContentsTheSame (int a, int b) { return false; }
      });
      topics = next;
      diff.dispatchUpdatesTo(this);
    }

    @Override public RecyclerView.ViewHolder onCreateViewHolder (ViewGroup parent, int type) {
      View view;
      if (type == 0) {
        ForumTopicView row = new ForumTopicView(parent.getContext(), tdlib);
        row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(84)));
        row.setOnClickListener(v -> {
          TdApi.ForumTopic topic = row.getTopic();
          if (topic != null && !context().isNavigationBusy()) {
            tdlib.ui().openChat(ForumTopicsController.this, getChatId(), new TdlibUi.ChatOpenParameters().chatList(getArgumentsStrict().chatList).messageTopic(new TdApi.MessageTopicForum(topic.info.forumTopicId)).keepStack());
          }
        });
        view = row;
      } else {
        TextView footer = new TextView(parent.getContext());
        footer.setGravity(Gravity.CENTER);
        footer.setTextSize(14);
        footer.setPadding(Screen.dp(20), Screen.dp(24), Screen.dp(20), Screen.dp(24));
        footer.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addThemeTextColorListener(footer, ColorId.textLight);
        footer.setOnClickListener(v -> { if (session != null) session.retry(); });
        view = footer;
      }
      addThemeInvalidateListener(view);
      return new RecyclerView.ViewHolder(view) { };
    }

    @Override public void onBindViewHolder (RecyclerView.ViewHolder holder, int position) {
      if (holder.itemView instanceof ForumTopicView) {
        ((ForumTopicView) holder.itemView).setTopic(topics.get(position));
      } else {
        TextView footer = (TextView) holder.itemView;
        int text = snapshot == null || snapshot.loadingInitial || snapshot.loadingMore || snapshot.refreshing ? R.string.ForumTopicsLoading :
          snapshot.error != null ? R.string.ForumTopicsLoadFailed : topics.isEmpty() ? R.string.ForumTopicsEmpty : snapshot.stale ? R.string.ForumTopicsStale : 0;
        footer.setText(text != 0 ? Lang.getString(text) : "");
        footer.setTextColor(Theme.textDecentColor());
        footer.setEnabled(snapshot != null && (snapshot.error != null || snapshot.stale));
      }
    }
    @Override public void onViewRecycled (RecyclerView.ViewHolder holder) {
      if (holder.itemView instanceof ForumTopicView) ((ForumTopicView) holder.itemView).clear();
    }
  }
}
