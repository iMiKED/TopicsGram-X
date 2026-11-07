/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 07/11/2026
 */
package org.thunderdog.challegram.core;

import android.os.Build;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@me.vkryl.annotation.MadeByLLM(value = "gpt-5", id = "legacy-api-compat")
public class ExecutorSerialQueueLegacyTest {
  private ExecutorSerialQueue queue;
  private ScheduledThreadPoolExecutor executor;

  @Before
  public void createQueue () throws Exception {
    // The mockable Android jar selects the pre-21 branch; this is not a Dalvik runtime test.
    assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP);
    queue = new ExecutorSerialQueue("LegacyQueueTest", Thread.NORM_PRIORITY, null);
    Field field = ExecutorSerialQueue.class.getDeclaredField("executor");
    field.setAccessible(true);
    executor = (ScheduledThreadPoolExecutor) field.get(queue);
    assertFalse(executor.getRemoveOnCancelPolicy());
  }

  @After
  public void stopQueue () throws Exception {
    if (queue != null) {
      queue.quit(false);
    }
    if (executor != null) {
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  public void cancelledRunnableIsRemovedBeforeItsLongDelay () {
    Runnable cancelled = () -> fail("Cancelled work ran");
    Runnable retained = () -> fail("Delayed work ran early");
    queue.post(cancelled, TimeUnit.HOURS.toMillis(1));
    queue.post(retained, TimeUnit.HOURS.toMillis(1));
    assertEquals(2, executor.getQueue().size());
    queue.cancel(cancelled);
    assertEquals(1, executor.getQueue().size());
    queue.cancel(retained);
    assertTrue(executor.getQueue().isEmpty());
  }

  @Test
  public void cancelledMessagesKeepIdentityAndWildcardSemantics () {
    Object first = new Object();
    Object second = new Object();
    queue.send(7, 0, 0, first, TimeUnit.HOURS.toMillis(1));
    queue.send(7, 0, 0, second, TimeUnit.HOURS.toMillis(1));
    queue.cancel(7, first);
    assertFalse(queue.has(7, first));
    assertTrue(queue.has(7, second));
    assertEquals(1, executor.getQueue().size());
    queue.cancel(7, null);
    assertFalse(queue.has(7, null));
    assertTrue(executor.getQueue().isEmpty());
  }

  @Test
  public void safeQuitRemovesNotYetDueWork () {
    queue.post(() -> fail("Delayed work ran early"), TimeUnit.HOURS.toMillis(1));
    queue.quit(true);
    assertTrue(executor.getQueue().isEmpty());
  }

  @Test
  public void immediateQuitRemovesNotYetDueWork () {
    queue.post(() -> fail("Delayed work ran early"), TimeUnit.HOURS.toMillis(1));
    queue.quit(false);
    assertTrue(executor.getQueue().isEmpty());
  }
}
