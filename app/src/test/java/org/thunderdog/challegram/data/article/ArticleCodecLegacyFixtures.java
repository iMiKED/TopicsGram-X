/* This file is a part of Telegram X. SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;

import java.util.Base64;

/** Run only with the original codec and TDLib to reproduce the frozen fixture bytes. */
public final class ArticleCodecLegacyFixtures {
  static final String SCHEMA = "af52186ba824bc43c04023ab301ea9a298ee929fd532d2fb44fc9eaec8bbb68f";

  private ArticleCodecLegacyFixtures () { }

  static TdApi.InternalLinkTypePublicChat publicChat () {
    TdApi.InternalLinkTypePublicChat link = new TdApi.InternalLinkTypePublicChat();
    link.chatUsername = "article_fixture";
    link.draftText = "Черновик 👩‍💻";
    link.openProfile = true;
    return link;
  }

  static TdApi.InternalLinkTypeUserPhoneNumber phoneNumber () {
    TdApi.InternalLinkTypeUserPhoneNumber link = new TdApi.InternalLinkTypeUserPhoneNumber();
    link.phoneNumber = "+15550100123";
    link.draftText = null;
    link.openProfile = false;
    return link;
  }

  static TdApi.InternalLinkTypeUserToken userToken () {
    TdApi.InternalLinkTypeUserToken link = new TdApi.InternalLinkTypeUserToken();
    link.token = "synthetic-user-token";
    return link;
  }

  private static TdApi.RichText button (String label, TdApi.InternalLinkType link) {
    return new TdApi.RichTextButton(new TdApi.InlineButton(
      new TdApi.RichTextPlain(label),
      new TdApi.ButtonStylePrimary(),
      new TdApi.InlineKeyboardButtonTypeSwitchInline(
        "fixture-query",
        new TdApi.TargetChatInternalLink(link)
      )
    ));
  }

  private static TdApi.RichText links () {
    return new TdApi.RichTextBold(new TdApi.RichTexts(new TdApi.RichText[] {
      new TdApi.RichTextPlain("Before"),
      button("Public", publicChat()),
      button("Phone", phoneNumber()),
      button("Token", userToken()),
      new TdApi.RichTextPlain("After")
    }));
  }

  static TdApi.InputRichMessage input () {
    return new TdApi.InputRichMessage(
      new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {
        new TdApi.InputPageBlockParagraph(links()),
        new TdApi.InputPageBlockDetails(
          new TdApi.RichTextPlain("Section"),
          new TdApi.InputPageBlock[] {
            new TdApi.InputPageBlockParagraph(new TdApi.RichTextUrl(
              new TdApi.RichTextPlain("Source"),
              "https://example.org/article",
              false
            ))
          },
          true
        )
      }),
      true,
      true
    );
  }

  static TdApi.RichMessage received () {
    return new TdApi.RichMessage(
      new TdApi.PageBlock[] {
        new TdApi.PageBlockParagraph(links()),
        new TdApi.PageBlockPhoto(
          null,
          new TdApi.PageBlockCaption(new TdApi.RichTextPlain("Caption"), null),
          "https://example.org/photo",
          true
        )
      },
      true,
      true
    );
  }

  public static void main (String[] args) {
    if (!SCHEMA.equals(ArticleCodec.SCHEMA) ||
        TdApi.InternalLinkTypePublicChat.CONSTRUCTOR != 1769614592 ||
        TdApi.InternalLinkTypeUserPhoneNumber.CONSTRUCTOR != 273398536 ||
        TdApi.InternalLinkTypeUserToken.CONSTRUCTOR != -1462248615) {
      throw new IllegalStateException(
        "Fixtures must be captured with the original codec and TDLib"
      );
    }
    print("publicChat", publicChat());
    print("phoneNumber", phoneNumber());
    print("userToken", userToken());
    print("input", input());
    print("received", received());
  }

  private static void print (String name, TdApi.Object value) {
    System.out.println(name + "=" + Base64.getEncoder().encodeToString(ArticleCodec.encode(value)));
  }
}
