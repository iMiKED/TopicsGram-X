/* This file is a part of Telegram X. SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import org.thunderdog.challegram.data.ForumArticleDrafts;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

public class ArticleCodecTest {
  private static final String[] FIXTURES = {
    "publicChat", "phoneNumber", "userToken", "input", "received"
  };

  private static byte[] fixture (String name) throws IOException {
    Properties fixtures = new Properties();
    try (InputStream stream = ArticleCodecTest.class.getResourceAsStream(
      "/article-codec/legacy-art1.properties"
    )) {
      assertNotNull("Missing frozen legacy fixtures", stream);
      fixtures.load(stream);
    }
    assertEquals(ArticleCodecLegacyFixtures.SCHEMA, fixtures.getProperty("schema"));
    return Base64.getDecoder().decode(fixtures.getProperty(name));
  }

  private static TdApi.Object expected (String name) {
    switch (name) {
      case "publicChat": return ArticleCodecLegacyFixtures.publicChat();
      case "phoneNumber": return ArticleCodecLegacyFixtures.phoneNumber();
      case "userToken": return ArticleCodecLegacyFixtures.userToken();
      case "input": return ArticleCodecLegacyFixtures.input();
      case "received": return ArticleCodecLegacyFixtures.received();
      default: throw new IllegalArgumentException(name);
    }
  }

  private static void setTokens (TdApi.Object value, String token) {
    ArticleCodec.visit(value, (node, depth) -> {
      if (node instanceof TdApi.InternalLinkTypePublicChat) {
        ((TdApi.InternalLinkTypePublicChat) node).unpaidMessagesToken = token;
      } else if (node instanceof TdApi.InternalLinkTypeUserPhoneNumber) {
        ((TdApi.InternalLinkTypeUserPhoneNumber) node).unpaidMessagesToken = token;
      } else if (node instanceof TdApi.InternalLinkTypeUserToken) {
        ((TdApi.InternalLinkTypeUserToken) node).unpaidMessagesToken = token;
      }
    });
  }

  private static List<String> tokens (TdApi.Object value) {
    List<String> result = new ArrayList<>();
    ArticleCodec.visit(value, (node, depth) -> {
      if (node instanceof TdApi.InternalLinkTypePublicChat) {
        result.add(((TdApi.InternalLinkTypePublicChat) node).unpaidMessagesToken);
      } else if (node instanceof TdApi.InternalLinkTypeUserPhoneNumber) {
        result.add(((TdApi.InternalLinkTypeUserPhoneNumber) node).unpaidMessagesToken);
      } else if (node instanceof TdApi.InternalLinkTypeUserToken) {
        result.add(((TdApi.InternalLinkTypeUserToken) node).unpaidMessagesToken);
      }
    });
    return result;
  }

  private static String schema (byte[] bytes) throws IOException {
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
    assertEquals(0x41525431, input.readInt());
    return input.readUTF();
  }

  private static int payloadOffset (byte[] bytes) throws IOException {
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
    input.readInt();
    input.readUTF();
    return bytes.length - input.available();
  }

  private static byte[] withSchema (byte[] bytes, String schema) throws IOException {
    byte[] result = bytes.clone();
    byte[] replacement = schema.getBytes(StandardCharsets.US_ASCII);
    assertEquals(payloadOffset(bytes) - 6, replacement.length);
    System.arraycopy(replacement, 0, result, 6, replacement.length);
    return result;
  }

  private static byte[] withInt (byte[] bytes, int offset, int value) {
    byte[] result = bytes.clone();
    ByteBuffer.wrap(result).putInt(offset, value);
    return result;
  }

  @Test public void legacyFixturesReadCopyAndResaveWithoutLosingFields () throws Exception {
    assertNotEquals(ArticleCodecLegacyFixtures.SCHEMA, ArticleCodec.SCHEMA);
    for (String name : FIXTURES) {
      byte[] original = fixture(name);
      assertEquals(ArticleCodecLegacyFixtures.SCHEMA, schema(original));
      TdApi.Object value = ArticleCodec.decode(original);
      TdApi.Object expected = expected(name);
      setTokens(expected, "");
      byte[] saved = ArticleCodec.encode(value);
      assertEquals(ArticleCodec.SCHEMA, schema(saved));
      assertFalse(Arrays.equals(original, saved));
      assertArrayEquals(name, ArticleCodec.encode(expected), saved);
      assertArrayEquals(saved, ArticleCodec.encode(ArticleCodec.decode(saved)));
      TdApi.Object copy = ArticleCodec.copy(value, TdApi.Object.class);
      assertNotSame(value, copy);
      assertArrayEquals(saved, ArticleCodec.encode(copy));
      assertEquals(tokens(expected), tokens(value));
    }
  }

  @Test public void fixturesContainHistoricalWireIds () throws Exception {
    int[] constructors = {1769614592, 273398536, -1462248615};
    for (int index = 0; index < constructors.length; index++) {
      byte[] bytes = fixture(FIXTURES[index]);
      assertEquals(constructors[index], ByteBuffer.wrap(bytes).getInt(payloadOffset(bytes)));
    }
  }

  @Test public void legacyEditorDraftRestoresAndSavesInCurrentSchema () throws Exception {
    ArticleDocument restored = ArticleDocument.restore(fixture("input"));
    TdApi.InputRichMessage expected = ArticleCodecLegacyFixtures.input();
    setTokens(expected, "");
    assertEquals(new ArticleDocument(expected), restored);
    assertEquals(ArticleCodec.SCHEMA, schema(restored.save()));
    assertEquals(restored, ArticleDocument.restore(restored.save()));
    TdApi.InputRichMessage detached = restored.toInput();
    setTokens(detached, "changed-copy");
    assertEquals(Arrays.asList("", "", ""), tokens(restored.toInput()));
    assertTrue(detached.isRtl);
    assertTrue(detached.detectAutomaticBlocks);
  }

  @Test public void legacyForumDraftBodiesRestoreAndResaveForBothRoots () throws Exception {
    for (String name : new String[] {"input", "received"}) {
      int constructor = "input".equals(name) ?
        TdApi.DraftMessageContentInputRichMessage.CONSTRUCTOR :
        TdApi.DraftMessageContentRichMessage.CONSTRUCTOR;
      Map<String, Object> fields = new HashMap<>();
      fields.put("draft_content_constructor", constructor);
      fields.put("draft_article_body", fixture(name));
      fields.put("draft_date", 123);
      TdApi.DraftMessageContent content = ForumArticleDrafts.restore(fields);
      assertEquals(constructor, content.getConstructor());
      ForumArticleDrafts.put(fields, content);
      byte[] saved = (byte[]) fields.get("draft_article_body");
      assertEquals(ArticleCodec.SCHEMA, schema(saved));
      TdApi.Object expected = expected(name);
      setTokens(expected, "");
      assertArrayEquals(ArticleCodec.encode(expected), saved);
      assertEquals(123, fields.get("draft_date"));
      assertEquals(constructor, ForumArticleDrafts.restore(fields).getConstructor());
    }
  }

  @Test public void newTokensRoundTripAndCopyIncludingNullEmptyAndUnicode () throws Exception {
    for (String token : new String[] {null, "", "synthetic-unpaid-Ж 👩‍💻"}) {
      for (String name : FIXTURES) {
        TdApi.Object original = expected(name);
        setTokens(original, token);
        byte[] saved = ArticleCodec.encode(original);
        TdApi.Object restored = ArticleCodec.decode(saved);
        TdApi.Object copy = ArticleCodec.copy(original, TdApi.Object.class);
        List<String> expectedTokens = "input".equals(name) || "received".equals(name) ?
          Arrays.asList(token, token, token) : Arrays.asList(token);
        assertEquals(name, expectedTokens, tokens(restored));
        assertEquals(name, expectedTokens, tokens(copy));
        assertArrayEquals(saved, ArticleCodec.encode(restored));
        assertArrayEquals(saved, ArticleCodec.encode(copy));
        setTokens(copy, "changed-copy");
        assertEquals(expectedTokens, tokens(original));
      }
    }
  }

  @Test public void alternatingVersionsDoNotShareReadState () throws Exception {
    TdApi.InputRichMessage current = ArticleCodecLegacyFixtures.input();
    setTokens(current, "current-token");
    byte[] saved = ArticleCodec.encode(current);
    for (int count = 0; count < 5; count++) {
      assertEquals(Arrays.asList("", "", ""), tokens(ArticleCodec.decode(fixture("input"))));
      assertEquals(
        Arrays.asList("current-token", "current-token", "current-token"),
        tokens(ArticleCodec.decode(saved))
      );
    }
  }

  @Test public void unknownSchemaIsRejectedBeforeReadingPayload () throws Exception {
    for (byte[] bytes : new byte[][] {fixture("input"), ArticleCodec.encode(expected("input"))}) {
      byte[] unknown = withSchema(bytes, "0" + ArticleCodec.SCHEMA.substring(1));
      IOException error = assertThrows(IOException.class, () -> ArticleCodec.decode(unknown));
      assertEquals("Unsupported article snapshot version", error.getMessage());
    }
  }

  @Test public void allTruncationsAndTrailingDataAreRejectedInBothVersions () throws Exception {
    for (String name : FIXTURES) {
      byte[] legacy = fixture(name);
      byte[] current = ArticleCodec.encode(ArticleCodec.decode(legacy));
      for (byte[] bytes : new byte[][] {legacy, current}) {
        for (int length = 0; length < bytes.length; length++) {
          byte[] truncated = Arrays.copyOf(bytes, length);
          assertThrows(
            name + " length " + length,
            IOException.class,
            () -> ArticleCodec.decode(truncated)
          );
        }
        byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
        assertThrows(IOException.class, () -> ArticleCodec.decode(trailing));
      }
    }
  }

  @Test public void schemasCannotBeSwappedToAcceptWrongWireIds () throws Exception {
    for (String name : FIXTURES) {
      byte[] legacy = fixture(name);
      byte[] current = ArticleCodec.encode(ArticleCodec.decode(legacy));
      byte[] mislabeledCurrent = withSchema(current, ArticleCodecLegacyFixtures.SCHEMA);
      byte[] mislabeledLegacy = withSchema(legacy, ArticleCodec.SCHEMA);
      assertThrows(IOException.class, () -> ArticleCodec.decode(mislabeledCurrent));
      assertThrows(IOException.class, () -> ArticleCodec.decode(mislabeledLegacy));
    }
  }

  @Test public void currentOnlyConstructorsAreNotAcceptedAsLegacy () throws Exception {
    TdApi.Object[] values = {
      new TdApi.InternalLinkTypeTonConnect(), new TdApi.InternalLinkTypeTonWalletTransfer(),
      new TdApi.ProxyTypeWeb(), new TdApi.RichTextTonAddress(),
      new TdApi.TextEntityTypeTonAddress(),
      new TdApi.TonConnectConnectItemAddress(), new TdApi.TonConnectConnectItemProof(),
      new TdApi.TonConnectConnectRequest(), new TdApi.TonWalletTransferReceiverAddress(),
      new TdApi.TonWalletTransferReceiverUser()
    };
    for (TdApi.Object value : values) {
      byte[] current = ArticleCodec.encode(value);
      assertArrayEquals(current, ArticleCodec.encode(ArticleCodec.decode(current)));
      byte[] mislabeled = withSchema(current, ArticleCodecLegacyFixtures.SCHEMA);
      assertThrows(IOException.class, () -> ArticleCodec.decode(mislabeled));
    }
  }

  @Test public void invalidMagicConstructorLengthsAndUtf8AreRejected () throws Exception {
    byte[] legacy = fixture("publicChat");
    byte[] current = ArticleCodec.encode(ArticleCodec.decode(legacy));
    for (byte[] bytes : new byte[][] {legacy, current}) {
      int payload = payloadOffset(bytes);
      byte[][] corruptions = {
        withInt(bytes, 0, 0),
        withInt(bytes, payload, Integer.MAX_VALUE),
        withInt(bytes, payload + 4, -2),
        withInt(bytes, payload + 4, Integer.MAX_VALUE)
      };
      for (byte[] corrupted : corruptions) {
        assertThrows(IOException.class, () -> ArticleCodec.decode(corrupted));
      }
      byte[] malformedUtf8 = bytes.clone();
      malformedUtf8[payload + 8] = (byte) 0xff;
      assertThrows(IOException.class, () -> ArticleCodec.decode(malformedUtf8));
    }
    assertThrows(IOException.class, () -> ArticleCodec.decode(null));
    byte[] oversized = new byte[16 * 1024 * 1024 + 1];
    assertThrows(IOException.class, () -> ArticleCodec.decode(oversized));
  }

  @Test public void wrongNestedFieldTypeIsReportedAsIOException () throws Exception {
    byte[] current = ArticleCodec.encode(new TdApi.RichTextBold(new TdApi.RichTextPlain("text")));
    for (String schema : new String[] {ArticleCodec.SCHEMA, ArticleCodecLegacyFixtures.SCHEMA}) {
      byte[] invalid = withInt(
        withSchema(current, schema),
        payloadOffset(current) + 4,
        TdApi.InputFileId.CONSTRUCTOR
      );
      IOException error = assertThrows(IOException.class, () -> ArticleCodec.decode(invalid));
      assertTrue(error.getCause() instanceof ClassCastException);
    }
  }
}
