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

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@me.vkryl.annotation.MadeByLLM(value = "gpt-5", id = "biometric-api-contract")
public class BiometricAuthenticationApiContractTest {
  private String source;

  @Before
  public void readSourceContract () throws Exception {
    // RequiresApi is not a runtime guard. These checks do not execute Android 23/30 callbacks.
    String sourcePath = "app/src/main/java/org/thunderdog/challegram/core/" +
      "BiometricAuthentication.java";
    Path directory = Paths.get("").toAbsolutePath();
    while (directory != null) {
      Path candidate = directory.resolve(sourcePath);
      if (Files.isRegularFile(candidate)) {
        source = new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8)
          .replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "")
          .replaceAll("\\s+", " ");
        return;
      }
      directory = directory.getParent();
    }
    throw new AssertionError("Run from the checkout or its app directory: " + sourcePath);
  }

  @Test
  public void onlyAuthenticationRequiresApi23 () {
    int classDeclaration = source.indexOf("public class BiometricAuthentication");
    assertTrue(classDeclaration >= 0);
    assertFalse(source.substring(0, classDeclaration).contains("@RequiresApi"));
    assertTrue(source.contains(
      "@RequiresApi(Build.VERSION_CODES.M) public static void authenticate ("
    ));
    assertEquals(1, occurrences("@RequiresApi("));
    assertFalse(source.contains("@SuppressLint"));
    assertFalse(source.contains("@SuppressWarnings"));
  }

  @Test
  public void managerCreationBoundaryRemains23 () {
    // Locks the 22/23 boundary in source; the host SDK=0 test covers the fallback execution.
    assertEquals(23, Build.VERSION_CODES.M);
    assertEquals(
      "if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { " +
        "return BiometricManager.from(AppContext.get()); } else { return null; }",
      methodBody("getManager")
    );
  }

  @Test
  public void fingerprintLabelBoundaryRemains30 () {
    // This is the 29/30 naming boundary, not an authentication capability check.
    assertEquals(30, Build.VERSION_CODES.R);
    assertTrue(source.contains(
      "public static final boolean ONLY_FINGERPRINT = " +
        "Build.VERSION.SDK_INT < Build.VERSION_CODES.R;"
    ));
  }

  @Test
  public void promptStillRequestsBiometricsAndRejectsCredentialResults () {
    String authenticate = methodBody("authenticate");
    assertTrue(authenticate.contains(
      ".setAllowedAuthenticators( strong ? " +
        "BiometricManager.Authenticators.BIOMETRIC_STRONG : " +
        "BiometricManager.Authenticators.BIOMETRIC_WEAK )"
    ));
    assertTrue(authenticate.contains(".setNegativeButtonText(negativeButtonText)"));
    assertFalse(authenticate.contains("DEVICE_CREDENTIAL"));
    assertEquals(
      "int authenticationType = result.getAuthenticationType(); switch (authenticationType) { " +
        "case BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL: " +
        "case BiometricPrompt.AUTHENTICATION_RESULT_TYPE_UNKNOWN: " +
        "callback.onAuthenticationError(" +
        "\"Use biometrics instead of lock screen credentials.\", true); break; " +
        "case BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC: " +
        "callback.onAuthenticated(result, strong); break; default: " +
        "throw new UnsupportedOperationException(Integer.toString(authenticationType)); } " +
        "onComplete();",
      methodBody("onAuthenticationSucceeded")
    );
  }

  private int occurrences (String text) {
    int count = 0;
    for (int index = source.indexOf(text); index >= 0; index = source.indexOf(text, index + 1)) {
      count++;
    }
    return count;
  }

  private String methodBody (String name) {
    Matcher declaration = Pattern.compile(
      "\\b" + Pattern.quote(name) + "\\s*\\([^{};]*\\)\\s*\\{"
    ).matcher(source);
    assertTrue("Missing method: " + name, declaration.find());
    int start = declaration.end();
    int depth = 1;
    for (int index = start; index < source.length(); index++) {
      char character = source.charAt(index);
      if (character == '{') {
        depth++;
      } else if (character == '}' && --depth == 0) {
        return source.substring(start, index).trim();
      }
    }
    throw new AssertionError("Unclosed method: " + name);
  }
}
