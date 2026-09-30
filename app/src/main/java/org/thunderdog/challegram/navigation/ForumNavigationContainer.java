package org.thunderdog.challegram.navigation;

import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.ForumRailLayout;
import org.thunderdog.challegram.telegram.CleanupStartupDelegate;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.ForumTopicsController;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.ChatRailView;

import java.util.LinkedHashMap;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Resizes the existing navigation root, including its header and composer. All screens still
 * belong to the one NavigationStack, so focus, IME, Back and predictive gestures keep their owner.
 */
public final class ForumNavigationContainer extends FrameLayoutFix implements NavigationStack.ChangeListener {
  private final NavigationController navigation;
  private final View content;
  private ChatRailView rail;
  private Tdlib tdlib;
  private TdApi.ChatList source;
  private boolean session, destroyed, switching, closing;
  private float reveal;
  private float revealTarget;
  private ValueAnimator animator;
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
          if (animator != null) animator.cancel();
          reveal = revealTarget = 0;
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
      if (animator != null) animator.cancel();
      reveal = revealTarget = 0;
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
      setRevealed(false);
      return;
    }
    if (session && (current instanceof MainController || current instanceof ChatsController)) {
      session = false;
      closing = true;
      setRevealed(false);
      return;
    }
    boolean show = session && current.tdlib() == tdlib &&
      (current instanceof ForumTopicsController || current instanceof MessagesController || current instanceof org.thunderdog.challegram.ui.ForumTopicProfileController);
    if (rail != null) {
      rail.setSelectedChat(current.getChatId());
      rail.updateTheme();
    }
    setRevealed(show);
  }

  private void setRevealed (boolean visible) {
    float target = visible ? 1f : 0f;
    if (animator != null && revealTarget == target && animator.isRunning()) return;
    revealTarget = target;
    if (animator != null) { animator.cancel(); animator = null; }
    if (target == reveal) { if (closing && target == 0f) closeSession(); return; }
    boolean animate = isAttachedToWindow() && !Settings.instance().needReduceMotion() && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled());
    if (!animate) { reveal = target; if (closing && target == 0f) closeSession(); requestLayout(); return; }
    animator = ValueAnimator.ofFloat(reveal, target);
    animator.setDuration(220);
    animator.setInterpolator(new DecelerateInterpolator());
    animator.addUpdateListener(value -> { reveal = (float) value.getAnimatedValue(); requestLayout(); });
    animator.addListener(new AnimatorListenerAdapter() {
      private boolean cancelled;
      @Override public void onAnimationCancel (Animator animation) { cancelled = true; }
      @Override public void onAnimationEnd (Animator animation) {
        if (!cancelled && closing && revealTarget == 0f) closeSession();
      }
    });
    animator.start();
  }

  public boolean isRailTouch (float x) {
    return ForumRailLayout.hitRail(getWidth(), ForumRailLayout.occupied(railWidth(getWidth()), reveal), x, Lang.rtl());
  }

  public float contentX (float x) {
    return ForumRailLayout.contentX(ForumRailLayout.occupied(railWidth(getWidth()), reveal), x, Lang.rtl());
  }

  public int targetContentWidth () {
    int width = getWidth() > 0 ? getWidth() : Screen.currentWidth();
    return Math.max(0, width - (session ? railWidth(width) : 0));
  }

  private int railWidth (int width) {
    float dpWidth = width / getResources().getDisplayMetrics().density;
    return Screen.dp(ForumRailLayout.widthDp(dpWidth));
  }

  public void setBottomInset (int value) { bottomInset = value; requestLayout(); }

  @Override protected void onMeasure (int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
    int railWidth = railWidth(width), occupied = ForumRailLayout.occupied(railWidth, reveal);
    if (rail != null) {
      rail.setInsets(HeaderView.getTopOffset(), bottomInset);
      rail.measure(MeasureSpec.makeMeasureSpec(railWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
    content.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width - occupied), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    setMeasuredDimension(width, height);
  }

  @Override protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    int width = right - left, height = bottom - top;
    int railWidth = railWidth(width), occupied = ForumRailLayout.occupied(railWidth, reveal);
    boolean rtl = Lang.rtl();
    if (rail != null) {
      int x = rtl ? width - occupied : occupied - railWidth;
      rail.layout(x, 0, x + railWidth, height);
      rail.setVisibility(occupied > 0 ? VISIBLE : INVISIBLE);
    }
    content.layout(rtl ? 0 : occupied, 0, rtl ? width - occupied : width, height);
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
  }

  public void destroy () {
    destroyed = true;
    if (animator != null) animator.cancel();
    navigation.getStack().removeChangeListener(this);
    closeSession();
  }
}
