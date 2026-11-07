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
package org.thunderdog.challegram.service;

import android.os.Build;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertTrue;

@me.vkryl.annotation.MadeByLLM(value = "gpt-5", id = "legacy-lollipop-api-contracts")
public class AudioServiceLegacyTest {
  private AudioService service;

  @Before
  public void createUninitializedService () {
    // Mockable Android selects pre-21. This checks Java control flow, not Dalvik or playback.
    assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP);
    service = new AudioService();
  }

  private void invoke (String name) throws Exception {
    Method method = AudioService.class.getDeclaredMethod(name);
    method.setAccessible(true);
    method.invoke(service);
  }

  private void setUnusableSession () throws Exception {
    Field session = AudioService.class.getDeclaredField("mediaSession");
    session.setAccessible(true);
    // A pre-21 call must not cast this object or dereference the absent track.
    session.set(service, new Object());
  }

  @Test
  public void metadataWithoutSessionIsSafe () throws Exception {
    invoke("setSessionMetadata");
  }

  @Test
  public void playbackWithoutSessionIsSafe () throws Exception {
    invoke("setSessionPlayState");
  }

  @Test
  public void metadataNeverUsesNewPlatformSessionOnLegacy () throws Exception {
    setUnusableSession();
    invoke("setSessionMetadata");
  }

  @Test
  public void playbackNeverUsesNewPlatformSessionOnLegacy () throws Exception {
    setUnusableSession();
    invoke("setSessionPlayState");
  }
}
