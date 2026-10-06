/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.article.ArticleCodec;
import org.thunderdog.challegram.data.article.ArticleDocument;
import java.io.IOException;
import java.util.Map;

/** Product integration: TdBundle currently omits both rich-message draft bodies. */
public final class ForumArticleDrafts {
  private static final String BODY = "draft_article_body";
  private static final String TYPE = "draft_content_constructor";
  private ForumArticleDrafts () { }

  public static void put (Map<String, Object> fields, TdApi.DraftMessageContent content) {
    if (content instanceof TdApi.DraftMessageContentInputRichMessage) {
      fields.put(TYPE, content.getConstructor());
      fields.put(BODY, ArticleCodec.encode(((TdApi.DraftMessageContentInputRichMessage) content).message));
    } else if (content instanceof TdApi.DraftMessageContentRichMessage) {
      fields.put(TYPE, content.getConstructor());
      fields.put(BODY, ArticleCodec.encode(((TdApi.DraftMessageContentRichMessage) content).message));
    }
  }

  public static TdApi.DraftMessageContent restore (Map<String, Object> fields) throws IOException {
    Object type = fields.get(TYPE);
    boolean input = Integer.valueOf(TdApi.DraftMessageContentInputRichMessage.CONSTRUCTOR).equals(type);
    boolean received = Integer.valueOf(TdApi.DraftMessageContentRichMessage.CONSTRUCTOR).equals(type);
    if (!input && !received) return null;
    if (!(fields.get(BODY) instanceof byte[])) throw new IOException("Missing rich-message draft body");
    TdApi.Object body = ArticleCodec.decode((byte[]) fields.get(BODY));
    if (input && body instanceof TdApi.InputRichMessage) return new TdApi.DraftMessageContentInputRichMessage((TdApi.InputRichMessage) body);
    if (received && body instanceof TdApi.RichMessage) return new TdApi.DraftMessageContentRichMessage((TdApi.RichMessage) body);
    throw new IOException("Invalid rich-message draft type");
  }

  public static boolean matches (TdApi.DraftMessage draft, ArticleDocument expected) {
    if (draft == null) return false;
    try {
      if (draft.content instanceof TdApi.DraftMessageContentInputRichMessage) return expected.equals(new ArticleDocument(((TdApi.DraftMessageContentInputRichMessage) draft.content).message));
      if (draft.content instanceof TdApi.DraftMessageContentRichMessage) return expected.equals(ArticleDocument.received(((TdApi.DraftMessageContentRichMessage) draft.content).message));
    } catch (IllegalArgumentException ignored) { }
    return false;
  }
}
