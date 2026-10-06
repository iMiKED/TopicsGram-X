/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import org.thunderdog.challegram.data.article.ArticleCodec;
import org.thunderdog.challegram.data.article.ArticleDocument;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.Assert.*;

public class ForumArticleDraftsTest {
  @Test public void preservesInputArticleWithLinksAndDraftMetadata () throws Exception {
    TdApi.InputRichMessage input = new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {
      new TdApi.InputPageBlockParagraph(new TdApi.RichTextUrl(new TdApi.RichTextPlain("Link"), "https://example.org/article", false))
    }), true, false);
    Map<String, Object> fields = new TreeMap<>(); fields.put("draft_replyTo_messageId", 42L); fields.put("draft_date", 123);
    ForumArticleDrafts.put(fields, new TdApi.DraftMessageContentInputRichMessage(input));
    Map<String, Object> restored = ForumDraftCodec.decode(ForumDraftCodec.encode(fields));
    TdApi.DraftMessageContentInputRichMessage content = (TdApi.DraftMessageContentInputRichMessage) ForumArticleDrafts.restore(restored);
    assertArrayEquals(ArticleCodec.encode(input), ArticleCodec.encode(content.message));
    assertEquals(42L, restored.get("draft_replyTo_messageId")); assertEquals(123, restored.get("draft_date"));
  }

  @Test public void preservesReceivedArticlesWithoutEditorConversion () throws Exception {
    TdApi.RichMessage article = new TdApi.RichMessage(); article.blocks = new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(new TdApi.RichTextPlain("Received"))}; article.isFull = true;
    Map<String, Object> fields = new TreeMap<>(); ForumArticleDrafts.put(fields, new TdApi.DraftMessageContentRichMessage(article));
    TdApi.DraftMessageContentRichMessage restored = (TdApi.DraftMessageContentRichMessage) ForumArticleDrafts.restore(ForumDraftCodec.decode(ForumDraftCodec.encode(fields)));
    assertArrayEquals(ArticleCodec.encode(article), ArticleCodec.encode(restored.message));
  }

  @Test public void onlyClearsTheDocumentThatWasSent () {
    ArticleDocument sent = ArticleDocument.empty();
    TdApi.DraftMessage draft = new TdApi.DraftMessage(null, 1, new TdApi.DraftMessageContentInputRichMessage(sent.toInput()), 0, null);
    assertTrue(ForumArticleDrafts.matches(draft, sent));
    TdApi.InputRichMessage changed = sent.toInput();
    ((TdApi.RichMessageSourceBlocks) changed.source).blocks[0] = new TdApi.InputPageBlockParagraph(new TdApi.RichTextPlain("Newer draft"));
    assertFalse(ForumArticleDrafts.matches(draft, new ArticleDocument(changed)));
    draft.content = new TdApi.DraftMessageContentText(new TdApi.FormattedText("Text draft", new TdApi.TextEntity[0]), null);
    assertFalse(ForumArticleDrafts.matches(draft, sent)); assertFalse(ForumArticleDrafts.matches(null, sent));
  }

  @Test(expected = IOException.class) public void missingBodyIsNeverSilentlyRestoredAsAnEmptyDraft () throws Exception {
    Map<String, Object> fields = new TreeMap<>(); fields.put("draft_content_constructor", TdApi.DraftMessageContentRichMessage.CONSTRUCTOR);
    ForumArticleDrafts.restore(fields);
  }

  @Test(expected = IOException.class) public void mismatchedBodyTypeIsRejected () throws Exception {
    Map<String, Object> fields = new TreeMap<>(); ForumArticleDrafts.put(fields, new TdApi.DraftMessageContentInputRichMessage(ArticleDocument.empty().toInput()));
    fields.put("draft_content_constructor", TdApi.DraftMessageContentRichMessage.CONSTRUCTOR);
    ForumArticleDrafts.restore(fields);
  }
}
