package org.thunderdog.challegram.stage8;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.SystemClock;

import org.thunderdog.challegram.component.dialogs.ForumEmojiSlotChecks;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Framework-only, finite instrumentation suite. No JUnit/AndroidX Test dependency is required.
 *
 * Integration (parent-owned build file; -Pstage8.synthetic=true): defaultConfig.testInstrumentationRunner =
 * "org.thunderdog.challegram.stage8.Stage8SyntheticInstrumentation".
 * Use a dedicated, debuggable applicationId ending in .stage8synthetic and pass
 * -e stage8Synthetic true. A fresh separate install on the same phone is supported; no global
 * offline mode/account action is needed. The synthetic target manifest MUST remove INTERNET
 * permission and all providers, services, receivers and activities/activity-aliases. The
 * pre-Application guard below rejects an ordinary manifest, including provider startup paths.
 * Never install over the base application or launch MainActivity. No account data is needed.
 *
 * Deliberately not an ActivityScenario, account-binding, real emoji-download, screenshot
 * golden, animated-navigation, or full Stage8 acceptance suite. Reflection seams fail loudly
 * when production fields/lambda shape change. See SyntheticEnvironment for their scope.
 */
public final class Stage8SyntheticInstrumentation extends Instrumentation {
  public interface Check { void run (SyntheticEnvironment environment) throws Exception; }

  public static final class Case {
    final String name;
    final Check check;
    public Case (String name, Check check) { this.name = name; this.check = check; }
  }

  @Override public Application newApplication (ClassLoader loader, String className, Context context)
      throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    // This hook runs before Application.onCreate; never construct BaseApplication at all.
    assertIsolatedPackage(context);
    StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build());
    return super.newApplication(loader, Application.class.getName(), context);
  }

  @SuppressWarnings("deprecation")
  private static void assertIsolatedPackage (Context context) {
    if (!context.getPackageName().endsWith(".stage8synthetic")) {
      throw new IllegalStateException("Stage8 requires a separate .stage8synthetic applicationId");
    }
    PackageManager manager = context.getPackageManager();
    try {
      PackageInfo info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_PROVIDERS |
        PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS | PackageManager.GET_ACTIVITIES);
      if (info.sharedUserId != null || (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0 ||
          manager.checkPermission(android.Manifest.permission.INTERNET, context.getPackageName()) == PackageManager.PERMISSION_GRANTED ||
          manager.checkPermission(android.Manifest.permission.ACCESS_NETWORK_STATE, context.getPackageName()) == PackageManager.PERMISSION_GRANTED ||
          (info.providers != null && info.providers.length != 0) ||
          (info.services != null && info.services.length != 0) ||
          (info.receivers != null && info.receivers.length != 0) ||
          (info.activities != null && info.activities.length != 0)) {
        throw new IllegalStateException("Unsafe synthetic target: require private debug UID, no network permissions, and no app components");
      }
    } catch (PackageManager.NameNotFoundException failure) {
      throw new IllegalStateException("Cannot validate synthetic target manifest", failure);
    }
  }

  @Override public void onCreate (Bundle arguments) {
    super.onCreate(arguments);
    if (arguments == null || !"true".equals(arguments.getString("stage8Synthetic"))) {
      Bundle result = new Bundle();
      result.putString("shortMsg", "Explicit -e stage8Synthetic true acknowledgement required");
      finish(Activity.RESULT_CANCELED, result);
      return;
    }
    start();
  }

  @Override public void onStart () {
    List<Case> cases = new ArrayList<>();
    ForumTopicRowRenderChecks.register(cases);
    ForumNavigationRenderChecks.register(cases);
    ForumRailTransitionChecks.register(cases);
    ForumRailCompositionChecks.register(cases);
    ForumEmojiSlotChecks.register(cases);
    org.thunderdog.challegram.ui.ForumTopicEditorSearchChecks.register(cases);
    Handler main = new Handler(Looper.getMainLooper());
    int failed = 0, completed = 0;
    long deadline = SystemClock.elapsedRealtime() + 90000;
    for (int index = 0; index < cases.size(); index++) {
      Case test = cases.get(index);
      Bundle status = new Bundle();
      status.putString("id", "Stage8Synthetic");
      status.putString("class", getClass().getName());
      status.putString("test", test.name);
      status.putInt("current", index + 1);
      status.putInt("numtests", cases.size());
      sendStatus(1, status);
      AtomicReference<Throwable> error = new AtomicReference<>();
      CountDownLatch done = new CountDownLatch(1);
      Runnable work = () -> {
        try (SyntheticEnvironment environment = new SyntheticEnvironment(getTargetContext())) {
          try {
            test.check.run(environment);
          } finally {
            environment.assertNoAccountInitialization();
          }
        } catch (Throwable failure) {
          error.set(failure);
        } finally {
          done.countDown();
        }
      };
      main.post(work);
      boolean finished = false;
      try {
        long remaining = deadline - SystemClock.elapsedRealtime();
        finished = remaining > 0 && done.await(Math.min(8000, remaining), TimeUnit.MILLISECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        error.set(interrupted);
      }
      if (!finished) {
        main.removeCallbacks(work);
        error.set(new AssertionError("Bound exceeded; aborting suite without queuing more UI work"));
      }
      Throwable failure = error.get();
      if (failure != null) {
        failed++;
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        status.putString("stack", trace.toString());
        status.putString("stream", "\nFAIL " + test.name + "\n" + trace);
      } else {
        status.putString("stream", "\nPASS " + test.name + "\n");
      }
      sendStatus(failure == null ? 0 : -2, status);
      completed++;
      if (!finished) break;
    }
    Bundle result = new Bundle();
    result.putInt("numtests", completed);
    result.putInt("numfailures", failed);
    result.putString("stream", "\nStage8 synthetic: " + completed + "/" + cases.size() +
      " completed, " + failed + " failed. Synthetic presentation checks only.\n");
    finish(failed == 0 && completed == cases.size() ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
  }
}
