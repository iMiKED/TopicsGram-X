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
package org.thunderdog.challegram.mediaview;

import android.os.Build;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@me.vkryl.annotation.MadeByLLM(value = "gpt-5", id = "legacy-lollipop-api-contracts")
public class VideoPlayerViewLegacyTest {
  @Before
  public void usesLegacyControlFlow () {
    // Framework methods are mocked. No decoder, GL, real Handler or Dalvik is exercised.
    assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP);
    assertFalse(VideoPlayerView.APPLY_CROP_EFFECTS);
  }

  private boolean cropLayoutEnabled (VideoPlayerView player) throws Exception {
    Field enabled = VideoPlayerView.class.getDeclaredField("enableCropping");
    enabled.setAccessible(true);
    return enabled.getBoolean(player);
  }

  @Test
  public void cropRequestKeepsLayoutFallback () throws Exception {
    assertTrue(cropLayoutEnabled(new VideoPlayerView(null, null, 0, true, null)));
  }

  @Test
  public void noCropRequestDoesNotEnableLayoutCropping () throws Exception {
    assertFalse(cropLayoutEnabled(new VideoPlayerView(null, null, 0, false, null)));
  }

  @Test
  public void absentMediaNeedsNoPlatformEffects () {
    new VideoPlayerView(null, null, 0, true, null).checkCrop();
  }
}
