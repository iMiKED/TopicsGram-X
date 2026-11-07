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

import androidx.biometric.BiometricManager;
import androidx.core.os.CancellationSignal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@me.vkryl.annotation.MadeByLLM(value = "gpt-5", id = "biometric-api-contract")
public class BiometricAuthenticationFallbackTest {
  private static final long TIMESTAMP_SENTINEL = 123456789L;

  private Field pendingSignal;
  private Field lastTimestamp;
  private Object originalSignal;
  private long originalTimestamp;

  @Before
  public void requireHostFallbackBranch () throws Exception {
    // AGP's mockable jar supplies 0, not Android 16/21 or a working biometric service.
    assertEquals(0, Build.VERSION.SDK_INT);
    pendingSignal = BiometricAuthentication.class.getDeclaredField("pendingSignal");
    pendingSignal.setAccessible(true);
    originalSignal = pendingSignal.get(null);
    lastTimestamp = BiometricAuthentication.class.getDeclaredField("lastTimestamp");
    lastTimestamp.setAccessible(true);
    originalTimestamp = lastTimestamp.getLong(null);
    assertNull(originalSignal);
    lastTimestamp.setLong(null, TIMESTAMP_SENTINEL);
  }

  @After
  public void restoreState () throws Exception {
    if (pendingSignal != null) {
      pendingSignal.set(null, originalSignal);
    }
    if (lastTimestamp != null) {
      lastTimestamp.setLong(null, originalTimestamp);
    }
  }

  @Test
  public void pre23QueriesFailClosedWithoutApplicationContext () throws Exception {
    // No AppContext.init(), prompt or Android service is supplied by this test.
    for (boolean onlyEnrolled : new boolean[] {false, true}) {
      assertFalse(BiometricAuthentication.isStrongAvailable(onlyEnrolled));
      assertEquals(
        BiometricAuthentication.Availability.UNAVAILABLE,
        BiometricAuthentication.getAvailability(onlyEnrolled)
      );
    }
    assertFalse(BiometricAuthentication.isAvailable());
    assertTrue(BiometricAuthentication.isMissingEnrolledData(false));
    assertTrue(BiometricAuthentication.isMissingEnrolledData(true));
    assertNull(pendingSignal.get(null));
    assertEquals(TIMESTAMP_SENTINEL, lastTimestamp.getLong(null));
  }

  @Test
  public void pre23UsesFingerprintLabel () {
    assertTrue(BiometricAuthentication.ONLY_FINGERPRINT);
  }

  @Test
  public void idleCancellationIsRepeatableBefore23 () throws Exception {
    BiometricAuthentication.cancelAuthentication();
    BiometricAuthentication.cancelAuthentication();
    assertNull(pendingSignal.get(null));
    assertEquals(TIMESTAMP_SENTINEL, lastTimestamp.getLong(null));
  }

  @Test
  public void cancellationClearsSignalAndNotifiesOnce () throws Exception {
    // Only the AndroidX signal/listener path is exercised, not a platform prompt callback.
    CancellationSignal signal = new CancellationSignal();
    AtomicInteger cancellationCount = new AtomicInteger();
    signal.setOnCancelListener(() -> cancellationCount.incrementAndGet());
    pendingSignal.set(null, signal);

    BiometricAuthentication.cancelAuthentication();
    BiometricAuthentication.cancelAuthentication();

    assertTrue(signal.isCanceled());
    assertEquals(1, cancellationCount.get());
    assertNull(pendingSignal.get(null));
    assertEquals(TIMESTAMP_SENTINEL, lastTimestamp.getLong(null));
  }

  @Test
  public void successfulStatusIsAvailableWithOrWithoutEnrollmentFilter () throws Exception {
    assertTrue(isAvailable(BiometricManager.BIOMETRIC_SUCCESS, false));
    assertTrue(isAvailable(BiometricManager.BIOMETRIC_SUCCESS, true));
  }

  @Test
  public void missingEnrollmentOnlyPassesHardwareAvailabilityQuery () throws Exception {
    assertTrue(isAvailable(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, false));
    assertFalse(isAvailable(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, true));
  }

  @Test
  public void errorsAndUnknownStatusesNeverBecomeAvailable () throws Exception {
    int[] statuses = {
      BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
      BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
      BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
      Integer.MIN_VALUE,
      Integer.MAX_VALUE
    };
    for (int status : statuses) {
      assertFalse(isAvailable(status, false));
      assertFalse(isAvailable(status, true));
    }
  }

  private static boolean isAvailable (int status, boolean onlyEnrolled) throws Exception {
    Method method = BiometricAuthentication.class.getDeclaredMethod(
      "isAvailable",
      int.class,
      boolean.class
    );
    method.setAccessible(true);
    return (boolean) method.invoke(null, status, onlyEnrolled);
  }
}
