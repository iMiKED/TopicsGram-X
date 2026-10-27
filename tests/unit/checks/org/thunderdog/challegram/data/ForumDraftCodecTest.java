package org.thunderdog.challegram.data;

import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-checks-package")
public class ForumDraftCodecTest {
  @Test public void roundTripsAllDraftPrimitiveTypes () throws Exception {
    Map<String, Object> fields = new HashMap<>();
    fields.put("text", "Synthetic draft: Привет 📨\nSecond line");
    fields.put("reply", Long.MAX_VALUE);
    fields.put("offset", 15);
    fields.put("disabled", true);
    fields.put("url", null);
    assertEquals(fields, ForumDraftCodec.decode(ForumDraftCodec.encode(fields)));
  }

  @Test public void explicitClearHasAStoredRepresentation () throws Exception {
    byte[] clear = ForumDraftCodec.encode(new HashMap<>());
    assertNotNull(clear);
    assertTrue(ForumDraftCodec.decode(clear).isEmpty());
  }

  @Test public void encodingIsDeterministicForAcknowledgements () {
    Map<String, Object> a = new LinkedHashMap<>(), b = new LinkedHashMap<>();
    a.put("text", "A"); a.put("reply", 1L);
    b.put("reply", 1L); b.put("text", "A");
    assertArrayEquals(ForumDraftCodec.encode(a), ForumDraftCodec.encode(b));
    b.put("text", "B");
    assertFalse(java.util.Arrays.equals(ForumDraftCodec.encode(a), ForumDraftCodec.encode(b)));
  }

  @Test public void longTextIsNotLimitedByWriteUtf () throws Exception {
    Map<String, Object> fields = new HashMap<>();
    fields.put("text", new String(new char[100000]).replace('\0', 'x'));
    assertEquals(fields, ForumDraftCodec.decode(ForumDraftCodec.encode(fields)));
  }

  @Test public void utf8EncodingRetainsVersionOneBytes () throws Exception {
    Map<String, Object> fields = new HashMap<>();
    fields.put("text", "\u041f\ud83d\udce8");
    byte[] stored = {
      0, 0, 0, 1, 0, 0, 0, 1,
      0, 0, 0, 4, 't', 'e', 'x', 't', 1,
      0, 0, 0, 6, (byte) 0xd0, (byte) 0x9f,
      (byte) 0xf0, (byte) 0x9f, (byte) 0x93, (byte) 0xa8
    };
    assertArrayEquals(stored, ForumDraftCodec.encode(fields));
    assertEquals(fields, ForumDraftCodec.decode(stored));
  }

  @Test public void malformedUtf8KeepsReplacementBehavior () throws Exception {
    byte[] stored = {
      0, 0, 0, 1, 0, 0, 0, 1,
      0, 0, 0, 4, 't', 'e', 'x', 't', 1,
      0, 0, 0, 1, (byte) 0xff
    };
    assertEquals("\ufffd", ForumDraftCodec.decode(stored).get("text"));
    Map<String, Object> fields = new HashMap<>();
    fields.put("text", "\ud800");
    stored[stored.length - 1] = '?';
    assertArrayEquals(stored, ForumDraftCodec.encode(fields));
  }

  @Test(expected = IOException.class) public void truncatedDraftIsRejected () throws Exception { ForumDraftCodec.decode(new byte[] {0, 0, 0}); }
  @Test(expected = IOException.class) public void unknownVersionIsRejected () throws Exception { ForumDraftCodec.decode(new byte[] {0, 0, 0, 2, 0, 0, 0, 0}); }
  @Test(expected = IOException.class) public void negativeCountIsRejected () throws Exception { ForumDraftCodec.decode(new byte[] {0, 0, 0, 1, -1, -1, -1, -1}); }
  @Test(expected = IOException.class) public void trailingDataIsRejected () throws Exception { ForumDraftCodec.decode(new byte[] {0, 0, 0, 1, 0, 0, 0, 0, 1}); }
  @Test(expected = IOException.class) public void invalidLengthIsRejected () throws Exception { ForumDraftCodec.decode(new byte[] {0, 0, 0, 1, 0, 0, 0, 1, -1, -1, -1, -1, 0}); }
}