package org.thunderdog.challegram.stage8;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import org.thunderdog.challegram.navigation.ForumNavigationContainer;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ForumEditorTransitionChecks;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Screen;

import java.util.LinkedHashMap;
import java.util.List;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.get;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.invoke;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.measure;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.set;

/**
 * Real navigation-container measurement, layout, hit mapping, raster output and teardown.
 * Does NOT recreate ForumRailLayout's pure tests. Rail population/avatar rendering and
 * Activity/IME/predictive-Back/account-switch lifecycle are deliberately outside this seam.
 */
final class ForumNavigationRenderChecks {
  private static final int CONTENT_COLOR = 0xff168c6e;

  static void register (List<Stage8SyntheticInstrumentation.Case> cases) {
    for (int width : new int[] {320, 360, 412, 600}) {
      for (boolean rtl : new boolean[] {false, true}) {
        cases.add(new Stage8SyntheticInstrumentation.Case("navigation_view_geometry_w" + width + "_rtl" + rtl,
          env -> geometry(env, width, rtl)));
      }
    }
    cases.add(new Stage8SyntheticInstrumentation.Case("navigation_teardown_listener_and_saved_state", ForumNavigationRenderChecks::teardown));
    cases.add(new Stage8SyntheticInstrumentation.Case("navigation_empty_stack_releases_content_width", ForumNavigationRenderChecks::emptyStack));
    ForumEditorTransitionChecks.register(cases);
  }

  private static void geometry (SyntheticEnvironment env, int widthDp, boolean rtl) throws Exception {
    Context context = env.configure(1f, rtl, ThemeId.BLUE);
    NavigationController navigation = new NavigationController(context);
    View content = new View(context);
    content.setBackgroundColor(CONTENT_COLOR);
    ForumNavigationContainer root = new ForumNavigationContainer(context, navigation, content);
    try {
      int width = Screen.dp(widthDp), height = Screen.dp(240);
      int railWidth = Screen.dp(widthDp == 320 ? 56 : widthDp == 600 ? 72 : 64);
      for (float reveal : new float[] {0f, .5f, 1f}) {
        // No enable(): that would create a live TDLib-backed chat-list slice.
        set(root, "reveal", reveal);
        // Match setRevealed/ValueAnimator: changing a reflected field alone leaves Android's
        // same-MeasureSpec cache valid and incorrectly reuses the previous content width.
        root.requestLayout();
        measure(root, width, height);
        int occupied = Math.round(railWidth * reveal);
        equal(width - occupied, content.getMeasuredWidth(), "Existing navigation root must be resized, not overlaid");
        equal(height, content.getMeasuredHeight(), "Rail must not shorten the content vertically");
        equal(rtl ? 0 : occupied, content.getLeft(), "Content's physical leading edge");
        equal(rtl ? width - occupied : width, content.getRight(), "Content's physical trailing edge");
        for (int x : new int[] {0, width / 2, width - 1}) {
          boolean outsideContent = x < content.getLeft() || x >= content.getRight();
          require(root.isRailTouch(x) == outsideContent, "Hit region must agree with laid-out content at " + x);
        }
        require(!root.isRailTouch(-1) && !root.isRailTouch(width), "Touches outside the viewport are never rail touches");
        equal(0, Math.round(root.contentX(content.getLeft())), "Gesture/content origin must map to local zero");
        equal(content.getWidth() - 1, Math.round(root.contentX(content.getRight() - 1)), "Gesture/content right edge must agree");
        try (RecordingCanvas canvas = new RecordingCanvas(width, height)) {
          root.draw(canvas);
          equal(CONTENT_COLOR, canvas.bitmap.getPixel(content.getLeft(), height / 2), "Resized content paints its first pixel");
          equal(CONTENT_COLOR, canvas.bitmap.getPixel(content.getRight() - 1, height / 2), "Resized content paints its last pixel");
          if (occupied > 0) {
            int outside = rtl ? content.getRight() : content.getLeft() - 1;
            equal(0, canvas.bitmap.getPixel(outside, height / 2), "Content must not paint over reserved rail space");
          }
        }
      }
    } finally { root.destroy(); }
  }

  @SuppressWarnings("unchecked")
  private static void teardown (SyntheticEnvironment env) throws Exception {
    Context context = env.configure(1f, false, ThemeId.BLUE);
    NavigationController navigation = new NavigationController(context);
    View content = new View(context);
    List<?> listeners = (List<?>) get(navigation.getStack(), "changeListeners");
    int before = listeners.size();
    ForumNavigationContainer root = new ForumNavigationContainer(context, navigation, content);
    try {
      equal(before + 1, listeners.size(), "Container registers one stack listener");
      require(listeners.contains(root), "Registered listener must be this container");
      LinkedHashMap<Long, Bundle> states = (LinkedHashMap<Long, Bundle>) get(root, "savedTopics");
      for (long id = 1; id <= 18; id++) {
        Bundle state = new Bundle();
        state.putInt("synthetic_scroll", (int) id);
        states.put(id, state);
      }
      states.get(2L); // A recently accessed topic should survive the LRU trim.
      invoke(root, "rememberTopics", new Class<?>[0]);
      equal(16, states.size(), "Navigation state cache must remain bounded");
      require(states.containsKey(2L) && !states.containsKey(1L) && !states.containsKey(3L), "LRU trim must retain recently accessed identity");
      Bundle output = new Bundle();
      root.saveState(output);
      require(output.isEmpty(), "Inactive synthetic container must not persist an account identity");
      invoke(root, "setRevealed", new Class<?>[] {boolean.class}, true);
      require(get(root, "animator") == null, "Detached container must not start an animation");
      root.destroy();
      root.destroy();
      equal(before, listeners.size(), "Destroy must unregister exactly once");
      require(!listeners.contains(root) && states.isEmpty(), "Destroy releases subscriptions and saved topic state");
      require(!(Boolean) get(root, "session") && get(root, "rail") == null && get(root, "tdlib") == null,
        "Destroy must leave no rail session or account reference");
      require(content.getParent() == root, "Rail teardown must not destroy the existing navigation content");
      navigation.getStack().set(new ViewController<?>[0]);
      require(states.isEmpty(), "Later stack changes must not repopulate a destroyed container");
    } finally { root.destroy(); }
  }

  private static void emptyStack (SyntheticEnvironment env) throws Exception {
    Context context = env.configure(1f, false, ThemeId.BLUE);
    View content = new View(context);
    ForumNavigationContainer root = new ForumNavigationContainer(context, new NavigationController(context), content);
    try {
      invoke(root, "setRevealed", new Class<?>[] {boolean.class}, true);
      measure(root, Screen.dp(360), Screen.dp(240));
      require(content.getWidth() < root.getWidth(), "Fixture must begin with reserved rail width");
      root.refresh(); // Resolves the actual empty stack; never opens a controller or account.
      measure(root, Screen.dp(360), Screen.dp(240));
      equal(root.getWidth(), content.getWidth(), "An empty stack must release the rail's reserved width");
      require(!root.isRailTouch(0), "No stale rail hit area after session close");
    } finally { root.destroy(); }
  }
}
