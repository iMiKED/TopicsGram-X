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
package org.thunderdog.challegram.telegram;

import android.os.Build;

import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import me.vkryl.android.LocaleUtils;

import static org.junit.Assert.*;

@me.vkryl.annotation.MadeByLLM(value = "gpt-5", id = "legacy-api-compat")
public class LegacyLocaleCompatibilityTest {
  @Before
  public void usesLegacyBranch () {
    // Only the compatibility formatter is exercised, not DateManager's Android initialization.
    assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP);
  }

  @Test
  public void languageAndRegionRemainDistinct () {
    assertEquals("en-US", LocaleUtils.toBcp47Language(Locale.US));
    assertEquals("en-GB", LocaleUtils.toBcp47Language(Locale.UK));
    assertEquals("ru-RU", LocaleUtils.toBcp47Language(new Locale("ru", "RU")));
    assertEquals("fr-CA", LocaleUtils.toBcp47Language(Locale.CANADA_FRENCH));
  }

  @Test
  public void supportedVariantsAndLegacyLanguageAliasesAreRetained () {
    assertEquals("en-US-POSIX", LocaleUtils.toBcp47Language(new Locale("en", "US", "POSIX")));
    assertEquals("nn-NO", LocaleUtils.toBcp47Language(new Locale("no", "NO", "NY")));
    assertEquals("he-IL", LocaleUtils.toBcp47Language(new Locale("iw", "IL")));
    assertEquals("id-ID", LocaleUtils.toBcp47Language(new Locale("in", "ID")));
  }

  @Test
  public void rootLocaleHasDeterministicKey () {
    assertEquals("und", LocaleUtils.toBcp47Language(Locale.ROOT));
    assertEquals(
      LocaleUtils.toBcp47Language(new Locale("en", "US")),
      LocaleUtils.toBcp47Language(new Locale("en", "US"))
    );
  }
}
