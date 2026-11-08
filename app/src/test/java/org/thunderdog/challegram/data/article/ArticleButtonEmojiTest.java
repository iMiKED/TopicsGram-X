/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.*;

public class ArticleButtonEmojiTest {
  @Test public void repeatedCustomEmojiInButtonRowSurvivesArticleSnapshot () throws Exception {
    long emojiId = 5881720994749486901L;
    TdApi.RichText label = new TdApi.RichTexts(new TdApi.RichText[] {
      new TdApi.RichTextCustomEmoji(emojiId, "🦆"),
      new TdApi.RichTextPlain("Халява от утки"),
      new TdApi.RichTextCustomEmoji(emojiId, "🦆")
    });
    TdApi.PageBlockButtonRow row = new TdApi.PageBlockButtonRow(new TdApi.InlineButton[] {
      new TdApi.InlineButton(label, new TdApi.ButtonStylePrimary(), new TdApi.InlineKeyboardButtonTypeUrl("https://example.org/")),
      new TdApi.InlineButton(new TdApi.RichTextPlain("Plain"), null, new TdApi.InlineKeyboardButtonTypeDisabled())
    }, new TdApi.PageBlockHorizontalAlignmentCenter());
    TdApi.PageBlockButtonRow copy = (TdApi.PageBlockButtonRow) ArticleCodec.decode(ArticleCodec.encode(row));
    assertEquals(2, copy.buttons.length);
    assertTrue(copy.align instanceof TdApi.PageBlockHorizontalAlignmentCenter);
    assertTrue(copy.buttons[0].style instanceof TdApi.ButtonStylePrimary);
    assertEquals("https://example.org/", ((TdApi.InlineKeyboardButtonTypeUrl) copy.buttons[0].type).url);
    TdApi.RichText[] parts = ((TdApi.RichTexts) copy.buttons[0].text).texts;
    assertEquals(3, parts.length);
    assertEquals("Халява от утки", ((TdApi.RichTextPlain) parts[1]).text);
    for (int index : new int[] {0, 2}) {
      assertEquals(emojiId, ((TdApi.RichTextCustomEmoji) parts[index]).customEmojiId);
      assertEquals("🦆", ((TdApi.RichTextCustomEmoji) parts[index]).alternativeText);
    }
    assertEquals("Plain", ((TdApi.RichTextPlain) copy.buttons[1].text).text);
    assertTrue(copy.buttons[1].type instanceof TdApi.InlineKeyboardButtonTypeDisabled);
  }
}
