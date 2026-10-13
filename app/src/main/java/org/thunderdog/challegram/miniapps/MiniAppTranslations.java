/*
 * This file is a part of TopicsGram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.content.res.Configuration;
import android.content.res.Resources;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.UI;

/** Product fallback until these new keys are available in the Telegram X language packs. */
public final class MiniAppTranslations {
  private static Configuration cachedConfiguration;
  private static Resources cachedResources;
  private MiniAppTranslations () { }

  public static synchronized String get (TdApi.LanguagePackInfo language, String key, int resource) {
    if (!key.startsWith("MiniApp") || language == null) return null;
    boolean russian = "ru".equals(Lang.cleanLanguageCode(language.id)) || "ru".equals(Lang.cleanLanguageCode(language.baseLanguagePackId));
    Resources original = UI.getResources();
    Configuration configuration = new Configuration(original.getConfiguration());
    // The user's in-app language takes precedence over Android's system language, also on API16.
    java.util.Locale locale = russian ? new java.util.Locale("ru") : java.util.Locale.ENGLISH;
    if (android.os.Build.VERSION.SDK_INT >= 17) configuration.setLocale(locale);
    else configuration.locale = locale;
    if (cachedResources == null || !configuration.equals(cachedConfiguration)) {
      cachedConfiguration = configuration;
      cachedResources = new Resources(original.getAssets(), original.getDisplayMetrics(), configuration);
    }
    return cachedResources.getString(resource);
  }
}
