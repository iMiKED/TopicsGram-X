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
 * File created on 08/11/2026
 */
package org.thunderdog.challegram.ui;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Source contracts only: no Android touch dispatch, accessibility tree or rendering is simulated.
 */
@me.vkryl.annotation.MadeByLLM(value = "gpt-5", id = "article-preview-entry")
public class ArticleEditorPreviewEntryTest {
  private String controller;
  private String history;

  @Before
  public void readActualSource () throws IOException {
    controller = source("ui/ArticleEditorController.java");
    String layout = bodyAfter(controller, "privatevoidcreateEditorLayout(");
    int start = layout.indexOf("LinearLayouthistoryBar=");
    int end = layout.indexOf("editingArticle=editing;", start);
    assertTrue("History controls must exist in the editor layout", start >= 0 && end > start);
    history = layout.substring(start, end);
  }

  @Test
  public void previewHasAnExplicitLocalizedNormalClickEntry () {
    Matcher entry = Pattern.compile(
      "icon\\(historyBar,R\\.drawable\\.baseline_visibility_24," +
        "R\\.string\\.ArticlePreview,this::preview,44\\);"
    ).matcher(history);
    assertTrue(
      "Preview needs its own labelled icon routed through the guarded click helper",
      entry.find()
    );
    assertFalse("Only one Preview control is needed", entry.find());
    assertFalse(
      "The child-covered parent long-press is not an entry point",
      history.contains("setOnLongClickListener(")
    );
  }

  @Test
  public void historyAllocatesThreeEqualSlotsAndPreservesUndoRedo () {
    assertEquals(
      "Undo, Redo and Preview each need their own slot",
      3,
      count(history, "icon(historyBar,")
    );
    assertTrue(history.contains(
      "undoButton=icon(historyBar,R.drawable.article_iv_undo_24," +
        "R.string.ArticleUndo,()->restore(history.undo()),44);"
    ));
    assertTrue(history.contains(
      "redoButton=icon(historyBar,R.drawable.article_iv_redo_24," +
        "R.string.ArticleRedo,()->restore(history.redo()),44);"
    ));
    Pattern geometry = Pattern.compile(
      "historyParams=newFrameLayout\\.LayoutParams\\(Screen\\.dp\\((?:132|3\\*44|44\\*3)\\)," +
        "Screen\\.dp\\(44\\),Gravity\\.TOP\\|Gravity\\.RIGHT\\)"
    );
    assertTrue(
      "The attached history row must be 3 x 44dp wide and 44dp high",
      geometry.matcher(history).find()
    );
    assertTrue(history.contains("historyParams.setMargins(0,Screen.dp(8),Screen.dp(8),0);"));
    assertTrue(history.contains("page.addView(historyBar,historyParams);"));
    assertFalse(history.contains("historyBar.setOnLongClickListener("));
  }

  @Test
  public void normalClickRetainsSendingImportAndRecoveryGuards () {
    String helper = bodyAfter(controller, "privateImageViewicon(");
    String guard = "if(!sending&&!importing&&!recoveryPending)";
    assertTrue(helper.contains(
      "ArticleEditorPopup.icon(context(),resource,label,()->{" +
        guard + "{toolAnchor=parent;action.run();}})"
    ));
    assertTrue(helper.contains(
      "view.setOnClickListener(v->{" + guard + "{toolAnchor=v;action.run();}});"
    ));
    assertEquals("Both helper callbacks must remain guarded", 2, count(helper, "action.run();"));
    assertTrue(helper.contains(
      "parent.addView(view,newLinearLayout.LayoutParams(Screen.dp(width),Screen.dp(44)));"
    ));
  }

  @Test
  public void sharedIconHelperExposesTheLocalizedDescription () throws IOException {
    String helper = bodyAfter(
      source("widget/ArticleEditorPopup.java"),
      "publicstaticImageViewicon("
    );
    assertTrue(helper.contains("view.setImageResource(resource);"));
    assertTrue(helper.contains("view.setContentDescription(Lang.getString(description));"));
    assertTrue(helper.contains("view.setOnClickListener(v->action.run());"));
  }

  private static String source (String relativePath) throws IOException {
    String sourcePath = "app/src/main/java/org/thunderdog/challegram/" + relativePath;
    Path directory = Paths.get("").toAbsolutePath();
    while (directory != null) {
      Path candidate = directory.resolve(sourcePath);
      if (Files.isRegularFile(candidate)) {
        return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8)
          .replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "")
          .replaceAll("\\s+", "");
      }
      directory = directory.getParent();
    }
    throw new AssertionError("Run from the checkout or its app directory: " + sourcePath);
  }

  private static String bodyAfter (String text, String declaration) {
    int declarationStart = text.indexOf(declaration);
    assertTrue("Missing declaration: " + declaration, declarationStart >= 0);
    int opening = text.indexOf('{', declarationStart);
    assertTrue("Missing method body: " + declaration, opening >= 0);
    int depth = 1;
    for (int index = opening + 1; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == '{') {
        depth++;
      } else if (character == '}' && --depth == 0) {
        return text.substring(opening + 1, index);
      }
    }
    throw new AssertionError("Unclosed method: " + declaration);
  }

  private static int count (String text, String fragment) {
    int count = 0;
    int index = text.indexOf(fragment);
    while (index >= 0) {
      count++;
      index = text.indexOf(fragment, index + fragment.length());
    }
    return count;
  }
}
