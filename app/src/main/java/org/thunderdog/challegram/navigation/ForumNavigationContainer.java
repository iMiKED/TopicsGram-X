package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.ForumRailLayout;
import org.thunderdog.challegram.telegram.CleanupStartupDelegate;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.ForumTopicsController;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.ChatRailView;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Places the rail behind the full-width navigation header. Only attached topic-list bodies
 * reserve its width; messages, profiles and editors retain normal full-width navigation.
 * Keeping the rail behind the stack also lets Back reveal it without resizing either screen.
 */
public final class ForumNavigationContainer extends FrameLayoutFix implements NavigationStack.ChangeListener {
  private final NavigationController navigation;
  private final View content;
  private ChatRailView rail;
  private Tdlib tdlib;
  private TdApi.ChatList source;
  private boolean session, destroyed, switching, closing;
  private final ArrayList<View> topicViews = new ArrayList<>();
  private int bottomInset;
  private CleanupStartupDelegate cleanupListener;
  private final LinkedHashMap<Long, Bundle> savedTopics = new LinkedHashMap<>(16, .75f, true);

  public ForumNavigationContainer (Context context, NavigationController navigation, View content) {
    super(context);
    this.navigation = navigation;
    this.content = content;
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(content);
    navigation.getStack().addChangeListener(this);
  }

  public void enable (Tdlib account, @Nullable TdApi.ChatList chatList) {
    TdApi.ChatList requested = chatList != null ? chatList : resolveSource(account);
    if (tdlib != account || source == null || !TD.makeChatListKey(source).equals(TD.makeChatListKey(requested))) {
      closeSession();
      tdlib = account;
      source = requested;
    }
    session = true;
    closing = false;
    if (rail == null) {
      // The existing header owns Back (including selection/search/IME); do not duplicate it.
      rail = new ChatRailView(getContext(), tdlib, source, this::openChat);
      for (ViewController<?> item : navigation.getStack().getAll()) {
        if (item.tdlib() != tdlib) continue;
        ChatsController chats = item instanceof ChatsController ? (ChatsController) item :
          item instanceof MainController ? ((MainController) item).getCurrentChatsController() : null;
        if (chats != null && TD.makeChatListKey(chats.chatList()).equals(TD.makeChatListKey(source))) {
          rail.restoreScrollPosition(chats.getForumRailPosition(), 0);
          break;
        }
      }
      addView(rail, 0, FrameLayoutFix.newParams(Screen.dp(64), ViewGroup.LayoutParams.MATCH_PARENT));
      final Tdlib boundAccount = tdlib;
      cleanupListener = new CleanupStartupDelegate() {
        private void clear () { post(() -> {
          if (tdlib != boundAccount) return;
          closeSession();
          requestLayout();
        }); }
        @Override public void onPerformUserCleanup () { clear(); }
        @Override public void onPerformRestart () { clear(); }
        @Override public void onPerformStartup (boolean afterRestart) { }
      };
      tdlib.listeners().addCleanupListener(cleanupListener);
    }
  }

  private TdApi.ChatList resolveSource (Tdlib account) {
    if (session && tdlib == account && source != null) return source;
    for (ViewController<?> c : navigation.getStack().getAll()) {
      if (c.tdlib() != account) continue;
      if (c instanceof ChatsController) return ((ChatsController) c).chatList();
      if (c instanceof MainController) {
        ChatsController chats = ((MainController) c).getCurrentChatsController();
        if (chats != null) return chats.chatList();
      }
    }
    return new TdApi.ChatListMain();
  }

  public boolean isActiveFor (Tdlib account) { return session && tdlib == account; }

  public void restoreTopics (ForumTopicsController controller) {
    if (!isActiveFor(controller.tdlib())) return;
    Bundle state = savedTopics.get(controller.getChatId());
    if (state != null) controller.restoreInstanceState(state, "rail_");
  }

  private void rememberTopics () {
    for (ViewController<?> c : navigation.getStack().getAll()) {
      if (c instanceof ForumTopicsController && c.tdlib() == tdlib) {
        ForumTopicsController topics = (ForumTopicsController) c;
        topics.clearTopicSelection();
        Bundle state = new Bundle();
        if (topics.saveInstanceState(state, "rail_")) savedTopics.put(c.getChatId(), state);
      }
    }
    while (savedTopics.size() > 16) savedTopics.remove(savedTopics.keySet().iterator().next());
  }

  private void openChat (long chatId) {
    ViewController<?> current = navigation.getCurrentStackItem();
    if (!session || switching || navigation.isAnimating() || current == null || current.tdlib() != tdlib || current.getChatId() == chatId) return;
    rememberTopics();
    current.hideSoftwareKeyboard();
    switching = true;
    tdlib.ui().openChat(current, chatId, new TdlibUi.ChatOpenParameters().chatList(source).onDone(() -> switching = false));
  }

  @Override public void onStackChanged (NavigationStack stack) {
    if (destroyed) return;
    ViewController<?> current = stack.getCurrent();
    // A cleared stack during initController is transient. Evaluate after that transaction.
    if (current == null) { post(this::refresh); return; }
    refresh();
  }

  public void refresh () {
    if (destroyed) return;
    ViewController<?> current = navigation.getCurrentStackItem();
    if (current == null) {
      closeSession();
      requestLayout();
      return;
    }
    if (current instanceof ForumTopicsController) {
      ForumTopicsController topics = (ForumTopicsController) current;
      enable(topics.tdlib(), topics.getArgumentsStrict().chatList);
    } else if (current instanceof MessagesController && current.getChatId() != 0 && current.tdlib().isForum(current.getChatId())) {
      enable(current.tdlib(), ((MessagesController) current).chatList());
    }
    if (session && current.tdlib() != tdlib) {
      closeSession();
      return;
    }
    if (session && (current instanceof MainController || current instanceof ChatsController)) {
      closing = true;
      if (topicViews.isEmpty()) closeSession();
      requestLayout();
      return;
    }
    if (rail != null) {
      rail.setSelectedChat(current.getChatId());
      rail.updateTheme();
    }
    requestLayout();
  }

  void addTopicView (View view) {
    if (!topicViews.contains(view)) topicViews.add(view);
    requestLayout();
  }

  void removeTopicView (View view) {
    topicViews.remove(view);
    setTopicInset(view, 0);
    if (closing && topicViews.isEmpty()) closeSession();
    requestLayout();
  }

  private int occupiedWidth () {
    return session && !topicViews.isEmpty() ? railWidth(getWidth()) : 0;
  }

  private int headerBottom () {
    HeaderView header = navigation.getHeaderView();
    return header != null ? Math.round(header.getCurrentHeight()) + HeaderView.getTopOffset() : HeaderView.getSize(true);
  }

  public boolean isRailTouch (float x, float y) {
    return ForumRailLayout.hitRail(getWidth(), occupiedWidth(), x, y, headerBottom(), getHeight() - bottomInset, Lang.rtl());
  }

  public float contentX (float x, float y) {
    return y >= headerBottom() ? ForumRailLayout.contentX(occupiedWidth(), x, Lang.rtl()) : x;
  }

  public int targetContentWidth () {
    // Message layout may be prepared while a topic list is still attached behind it.
    return getWidth() > 0 ? getWidth() : Screen.currentWidth();
  }

  private int railWidth (int width) {
    float dpWidth = width / getResources().getDisplayMetrics().density;
    return Screen.dp(ForumRailLayout.widthDp(dpWidth));
  }

  public void setBottomInset (int value) { bottomInset = value; requestLayout(); }

  private void setTopicInset (View view, int inset) {
    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
    int left = Lang.rtl() ? 0 : inset, right = Lang.rtl() ? inset : 0;
    if (params.leftMargin != left || params.rightMargin != right) {
      params.leftMargin = left;
      params.rightMargin = right;
      view.setLayoutParams(params);
      content.forceLayout();
    }
  }

  @Override protected void onMeasure (int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
    int railWidth = railWidth(width);
    for (View view : topicViews) setTopicInset(view, session ? railWidth : 0);
    if (rail != null) {
      rail.setInsets(headerBottom(), bottomInset);
      rail.measure(MeasureSpec.makeMeasureSpec(railWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
    content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    setMeasuredDimension(width, height);
  }

  @Override protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    int width = right - left, height = bottom - top;
    int railWidth = railWidth(width);
    boolean rtl = Lang.rtl();
    if (rail != null) {
      int x = rtl ? width - railWidth : 0;
      rail.layout(x, 0, x + railWidth, height);
      rail.setVisibility(session && !topicViews.isEmpty() ? VISIBLE : INVISIBLE);
    }
    content.layout(0, 0, width, height);
  }

  public void saveState (Bundle out) {
    if (!session || tdlib == null || source == null) return;
    out.putInt("forum_rail_account", tdlib.id());
    out.putString("forum_rail_list", TD.makeChatListKey(source));
    if (rail != null) {
      out.putInt("forum_rail_position", rail.scrollPosition());
      out.putInt("forum_rail_offset", rail.scrollOffset());
    }
  }

  public void restoreState (Bundle in, Tdlib account) {
    if (in.containsKey("forum_rail_list") && in.getInt("forum_rail_account", -1) == account.id()) {
      enable(account, TD.chatListFromKey(in.getString("forum_rail_list")));
      if (rail != null) rail.restoreScrollPosition(in.getInt("forum_rail_position", 0), in.getInt("forum_rail_offset", 0));
    }
  }

  private void closeSession () {
    if (tdlib != null && cleanupListener != null) tdlib.listeners().removeCleanupListener(cleanupListener);
    cleanupListener = null;
    if (rail != null) { rail.destroy(); removeView(rail); rail = null; }
    session = switching = closing = false;
    savedTopics.clear();
    tdlib = null; source = null;
    requestLayout();
  }

  public void destroy () {
    destroyed = true;
    navigation.getStack().removeChangeListener(this);
    closeSession();
    for (View view : topicViews) setTopicInset(view, 0);
    topicViews.clear();
  }
}
