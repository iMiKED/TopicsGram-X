package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;

/** Checks the product language fallback without changing the user's language setting. */
final class MiniAppTranslationDeviceChecks {
  static void run (MiniAppWebViewInstrumentation test) {
    TdApi.LanguagePackInfo russian = new TdApi.LanguagePackInfo();
    russian.id = "ru"; russian.baseLanguagePackId = "";
    TdApi.LanguagePackInfo english = new TdApi.LanguagePackInfo();
    english.id = "en"; english.baseLanguagePackId = "";
    TdApi.LanguagePackInfo custom = new TdApi.LanguagePackInfo();
    custom.id = "Xsynthetic"; custom.baseLanguagePackId = "ru";
    test.check("Открыть приложение".equals(Lang.getString(russian, R.string.MiniAppOpen)), "Russian in-app pack localizes Open App");
    test.check("Open App".equals(Lang.getString(english, R.string.MiniAppOpen)), "English in-app pack stays English independently of system locale");
    test.check("Закрыть".equals(MiniAppTranslations.get(custom, "MiniAppFullscreenClose", R.string.MiniAppFullscreenClose)), "custom Russian pack inherits local Mini Apps strings");
    String age = Lang.getString(russian, R.string.MiniAppAgeVerificationText, 21);
    test.check(age.contains("21") && age.contains("камеры"), "Russian age consent preserves its numeric placeholder");
    test.check(Lang.getString(russian, R.string.MiniAppWebViewUnavailable).startsWith("Для мини-приложений"), "unsupported platform explanation is translated");
    test.check(MiniAppTranslations.get(russian, "Cancel", R.string.Cancel) == null, "fallback leaves other product strings untouched");
  }
}
