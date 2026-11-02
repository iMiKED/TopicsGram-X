#!/usr/bin/env python3
"""Generate the bounded, reflection-free article snapshot codec from pinned TdApi.java.

Run from any directory. The generated source is checked in, so Python is not a
build/runtime dependency. Only the transitive closure of the roots below is used.
"""
import argparse
import hashlib
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument(
  "--source",
  type=Path,
  default=ROOT / "tdlib/src/main/java/org/drinkless/tdlib/TdApi.java"
)
parser.add_argument("--check", action="store_true", help="Check without writing generated code")
args = parser.parse_args()
SOURCE = args.source
DEST = ROOT / "app/src/main/java/org/thunderdog/challegram/data/article/ArticleCodec.java"

# The only supported migration: c15d3f5a -> stock 4c6377aa (TDLib wrapper f896c3f2).
# Keep the old fingerprint and wire IDs: old snapshots contain neither new IDs nor tokens.
LEGACY_SCHEMA = "af52186ba824bc43c04023ab301ea9a298ee929fd532d2fb44fc9eaec8bbb68f"
LEGACY_CONSTRUCTORS_HASH = "b5553a0e330475175398ec7f7ef8bd6e1ef40630e58897cc0e5ac0f47315887d"
CURRENT_SCHEMA = "6c90532b6d9c78c56d1723866d7d495d4a54cb87444c38cfbdcfbe76de0e6333"
CURRENT_CONSTRUCTORS_HASH = "b78a97a9dd42431d72c45539d7ea9d8e047b45b2ce624726b4cc64110554c22e"
TOKEN_CONSTRUCTORS = {
  "InternalLinkTypePublicChat": (1769614592, -1978459296),
  "InternalLinkTypeUserPhoneNumber": (273398536, 1068134051),
  "InternalLinkTypeUserToken": (-1462248615, -1224824778)
}
# These are in the current root closure, but not in the frozen 281-constructor codec.
CURRENT_ONLY_TYPES = {
  "InternalLinkTypeTonConnect",
  "InternalLinkTypeTonWalletTransfer",
  "ProxyTypeWeb",
  "RichTextTonAddress",
  "TextEntityTypeTonAddress",
  "TonConnectConnectItemAddress",
  "TonConnectConnectItemProof",
  "TonConnectConnectRequest",
  "TonWalletTransferReceiverAddress",
  "TonWalletTransferReceiverUser"
}
text = SOURCE.read_text(encoding="utf-8")
source_commit = re.search(r'GIT_COMMIT_HASH = "([0-9a-f]+)";', text)[1]
classes = {}
constructor_ids = {}
matches = list(re.finditer(r"^    public (?:abstract )?static class (\w+)(?:<[^\n]+?>)? extends ([\w<>]+) \{", text, re.M))
for i, match in enumerate(matches):
    body = text[match.end():matches[i + 1].start() if i + 1 < len(matches) else len(text)]
    fields = re.findall(r"^        (?:@\w+\s+)*public (?:@\w+\s+)*([\w\[\]]+) (\w+);$", body, re.M)
    constructors = re.findall(r"^        public " + match[1] + r"\(([^)]*)\)", body, re.M)
    expected = max((len(c.split(",")) if c.strip() else 0 for c in constructors), default=0)
    if expected != len(fields):
        raise ValueError(f"Field/constructor mismatch in {match[1]}: {len(fields)} != {expected}")
    classes[match[1]] = (match[2], fields, "abstract" in match[0])
    constructor = re.search(r"public static final int CONSTRUCTOR = (-?\d+);", body)
    if constructor:
      constructor_ids[match[1]] = int(constructor[1])

primitive = {"boolean": "Boolean", "int": "Int", "long": "Long", "double": "Double", "byte": "Byte"}
pending = ["RichMessage", "InputRichMessage"]
selected = set()
while pending:
    name = pending.pop().replace("[]", "")
    if name in primitive or name == "String" or name in selected:
        continue
    if name not in classes:
        raise ValueError("Unknown type: " + name)
    selected.add(name)
    base, fields, abstract = classes[name]
    if abstract:
        # User-account articles use structured blocks. Including bot-only text
        # sources would pull the entire InputMessageContent API into this codec.
        pending.extend(["RichMessageSourceBlocks"] if name == "RichMessageSource" else
                       (n for n, (b, _, _) in classes.items() if b == name))
    else:
        pending.extend(t for t, _ in fields)
concrete = sorted(n for n in selected if not classes[n][2])
schema = "\n".join(n + repr(classes[n][1]) for n in concrete)
schema_hash = hashlib.sha256(schema.encode()).hexdigest()

# Fail closed if a later TDLib update needs a different migration.
for name, (_, current_id) in TOKEN_CONSTRUCTORS.items():
  if (constructor_ids.get(name) != current_id or
      classes[name][1][-1:] != [("String", "unpaidMessagesToken")]):
    raise ValueError("Expected stock unpaidMessagesToken model in " + name)
legacy_concrete = [name for name in concrete if name not in CURRENT_ONLY_TYPES]
legacy_schema = "\n".join(
  name + repr(classes[name][1][:-1] if name in TOKEN_CONSTRUCTORS else classes[name][1])
  for name in legacy_concrete
)
legacy_constructors = "\n".join(
  f"{name}:{TOKEN_CONSTRUCTORS[name][0] if name in TOKEN_CONSTRUCTORS else constructor_ids[name]}"
  for name in legacy_concrete
)
current_constructors = "\n".join(f"{name}:{constructor_ids[name]}" for name in concrete)
if (schema_hash != CURRENT_SCHEMA or
    hashlib.sha256(current_constructors.encode()).hexdigest() != CURRENT_CONSTRUCTORS_HASH or
    hashlib.sha256(legacy_schema.encode()).hexdigest() != LEGACY_SCHEMA or
    hashlib.sha256(legacy_constructors.encode()).hexdigest() != LEGACY_CONSTRUCTORS_HASH):
  raise ValueError("TDLib changed beyond the supported ART1 migration; review compatibility first")

header = '''/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Generated by scripts/generate-article-codec.py. Do not edit the switch tables.
 * TDLib source model: SOURCE_COMMIT.
 * Legacy ART1 model: c15d3f5a5de6e3ba5839822c451152e5e18bb700 (frozen test fixtures).
 */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

/** Versioned local snapshots; never used as a network protocol or Java serialization. */
public final class ArticleCodec {
  private ArticleCodec () { }
  private static final int MAGIC = 0x41525431;
  private static final int MAX_BYTES = 16 * 1024 * 1024;
  private static final int MAX_ITEMS = 100000;
  private static final int MAX_DEPTH = 128;
  private static final Charset UTF8 = Charset.forName("UTF-8");
  public static final String SCHEMA = "SCHEMA_HASH";
  private static final String LEGACY_SCHEMA = "LEGACY_SCHEMA_HASH";

  private static final class Budget {
    final boolean legacy;
    int items;

    Budget () {
      this(false);
    }

    Budget (boolean legacy) {
      this.legacy = legacy;
    }

    void enter (int depth) throws IOException {
      if (depth > MAX_DEPTH || ++items > MAX_ITEMS) throw new IOException("Article snapshot exceeds structural limits");
    }
  }

  public static byte[] encode (TdApi.Object value) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(new FilterOutputStream(bytes) {
        private int size;
        private void count (int length) throws IOException {
          if (length > MAX_BYTES - size) throw new IOException("Article snapshot is too large");
          size += length;
        }
        @Override public void write (int b) throws IOException { count(1); this.out.write(b); }
        @Override public void write (byte[] b, int offset, int length) throws IOException { count(length); this.out.write(b, offset, length); }
      });
      out.writeInt(MAGIC);
      out.writeUTF(SCHEMA);
      write(out, value, 0, new Budget());
      out.flush();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot snapshot article", e);
    }
  }

  public static TdApi.Object decode (byte[] bytes) throws IOException {
    if (bytes == null || bytes.length > MAX_BYTES) {
      throw new IOException("Invalid article snapshot size");
    }
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
    if (in.readInt() != MAGIC) {
      throw new IOException("Unsupported article snapshot version");
    }
    String schema = in.readUTF();
    boolean legacy = LEGACY_SCHEMA.equals(schema);
    if (!legacy && !SCHEMA.equals(schema)) {
      throw new IOException("Unsupported article snapshot version");
    }
    try {
      TdApi.Object result = read(in, 0, new Budget(legacy));
      if (in.available() != 0) {
        throw new IOException("Trailing article snapshot data");
      }
      return result;
    } catch (ClassCastException e) {
      throw new IOException("Invalid article snapshot field type", e);
    }
  }

  public static <T extends TdApi.Object> T copy (T value, Class<T> type) {
    try { return type.cast(decode(encode(value))); }
    catch (IOException e) { throw new IllegalArgumentException("Invalid article snapshot", e); }
  }

  private static int length (DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length < -1 || length > MAX_ITEMS || length > in.available()) throw new IOException("Invalid article array length");
    return length;
  }

  private static void writeString (DataOutputStream out, String value) throws IOException {
    if (value == null) { out.writeInt(-1); return; }
    ByteBuffer buffer = UTF8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
    int size = buffer.remaining();
    if (size > MAX_BYTES) throw new IOException("Article string is too large");
    out.writeInt(size);
    if (buffer.hasArray()) out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), size);
    else while (buffer.hasRemaining()) out.writeByte(buffer.get());
  }

  private static String readString (DataInputStream in) throws IOException {
    int size = in.readInt();
    if (size == -1) return null;
    if (size < 0 || size > MAX_BYTES || size > in.available()) throw new IOException("Invalid article string size");
    byte[] bytes = new byte[size];
    in.readFully(bytes);
    return UTF8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
  }

  private static void write (DataOutputStream out, TdApi.Object value, int depth, Budget budget) throws IOException {
    budget.enter(depth);
    if (value == null) { out.writeInt(0); return; }
    out.writeInt(value.getConstructor());
    switch (value.getConstructor()) {
'''.replace("LEGACY_SCHEMA_HASH", LEGACY_SCHEMA).replace("SCHEMA_HASH", schema_hash).replace(
  "SOURCE_COMMIT", source_commit
)

def java_type(t):
    base = t.replace("[]", "")
    return t if base in primitive or base == "String" else "TdApi." + t

def write_field(t, expr, indent, level=0):
    if t.endswith("[]"):
        idx = "i" + str(level)
        return [indent + f"out.writeInt({expr} == null ? -1 : {expr}.length);",
                indent + f"if ({expr} != null) {{",
                indent + f"  if ({expr}.length > MAX_ITEMS) {{",
                indent + '    throw new IOException("Article array is too large");',
                indent + "  }",
                indent + f"  for (int {idx} = 0; {idx} < {expr}.length; {idx}++) {{"] + write_field(t[:-2], f"{expr}[{idx}]", indent + "    ", level + 1) + [indent + "  }", indent + "}"]
    if t == "String": return [indent + f"writeString(out, {expr});"]
    if t in primitive: return [indent + f"out.write{primitive[t]}({expr});"]
    return [indent + f"write(out, {expr}, depth + 1, budget);"]

def read_field(t, expr, indent, level=0):
    if t.endswith("[]"):
        idx, size = "i" + str(level), "n" + str(level)
        allocation = java_type(t).replace("[]", "[" + size + "]", 1)
        return [indent + "{", indent + f"  int {size} = length(in);",
                indent + f"  if ({size} >= 0) {{", indent + f"    {expr} = new {allocation};",
                indent + f"    for (int {idx} = 0; {idx} < {size}; {idx}++) {{"] + read_field(t[:-2], f"{expr}[{idx}]", indent + "      ", level + 1) + [indent + "    }", indent + "  }", indent + "}"]
    if t == "String": value = "readString(in)"
    elif t in primitive: value = f"in.read{primitive[t]}()"
    else: value = f"({java_type(t)}) read(in, depth + 1, budget)"
    return [indent + f"{expr} = {value};"]

lines = [header]
for name in concrete:
    lines.extend([f"      case TdApi.{name}.CONSTRUCTOR: {{", f"        TdApi.{name} v = (TdApi.{name}) value;"])
    for t, f in classes[name][1]: lines.extend(write_field(t, "v." + f, "        "))
    lines.extend(["        return;", "      }"])
lines.extend(['      default: throw new IOException("Unsupported article snapshot type: " + value.getConstructor());', "    }", "  }", "",
              "  private static TdApi.Object read (DataInputStream in, int depth, Budget budget) throws IOException {", "    budget.enter(depth);", "    int constructor = in.readInt();"])
lines.extend([
  "    if (budget.legacy) {",
  "      // Only legacy snapshots may use the three historical wire constructors.",
  "      switch (constructor) {"
])
for name, (legacy_id, _) in TOKEN_CONSTRUCTORS.items():
  lines.extend([
    f"        case {legacy_id}:",
    f"          constructor = TdApi.{name}.CONSTRUCTOR;",
    "          break;"
  ])
for name in TOKEN_CONSTRUCTORS:
  lines.append(f"        case TdApi.{name}.CONSTRUCTOR:")
for name in sorted(CURRENT_ONLY_TYPES):
  lines.append(f"        case TdApi.{name}.CONSTRUCTOR:")
lines.extend([
  '          throw new IOException("Constructor does not belong to legacy article schema");',
  "      }",
  "    }",
  "    switch (constructor) {",
  "      case 0: return null;"
])
for name in concrete:
    lines.extend([f"      case TdApi.{name}.CONSTRUCTOR: {{", f"        TdApi.{name} v = new TdApi.{name}();"])
    for t, f in classes[name][1]:
      if name in TOKEN_CONSTRUCTORS and f == "unpaidMessagesToken":
        lines.append('        v.unpaidMessagesToken = budget.legacy ? "" : readString(in);')
      else:
        lines.extend(read_field(t, "v." + f, "        "))
    lines.extend(["        return v;", "      }"])
lines.extend(['      default: throw new IOException("Unsupported article snapshot constructor: " + constructor);', "    }", "  }", "",
              "  public interface Visitor { void accept (TdApi.Object value, int depth); }",
              "  public static void visit (TdApi.Object value, Visitor visitor) {",
              "    try { visit(value, visitor, 0, new Budget()); }",
              "    catch (IOException e) { throw new IllegalArgumentException(\"Invalid article tree\", e); }",
              "  }",
              "  private static void visit (TdApi.Object value, Visitor visitor, int depth, Budget budget) throws IOException {",
              "    budget.enter(depth);", "    if (value == null) return;", "    visitor.accept(value, depth);", "    switch (value.getConstructor()) {"])
def visit_field(t, expr, indent, level=0):
    base = t.replace("[]", "")
    if base in primitive or base == "String": return []
    if t.endswith("[]"):
        idx = "i" + str(level)
        return [indent + f"if ({expr} != null) {{", indent + f"  for (int {idx} = 0; {idx} < {expr}.length; {idx}++) {{"] + visit_field(t[:-2], f"{expr}[{idx}]", indent + "    ", level + 1) + [indent + "  }", indent + "}"]
    return [indent + f"visit({expr}, visitor, depth + 1, budget);"]
for name in concrete:
    lines.extend([f"      case TdApi.{name}.CONSTRUCTOR: {{", f"        TdApi.{name} v = (TdApi.{name}) value;"])
    for t, f in classes[name][1]: lines.extend(visit_field(t, "v." + f, "        "))
    lines.extend(["        return;", "      }"])
lines.extend(['      default: throw new IOException("Unsupported article tree constructor: " + value.getConstructor());', "    }", "  }", "",
              "  /** Rewrites rich-text fields in place, including nested captions, table cells and wrappers. */",
              "  public static void transformRichTexts (TdApi.Object tree, java.util.function.UnaryOperator<TdApi.RichText> transform) {",
              "    visit(tree, (value, depth) -> {", "      switch (value.getConstructor()) {"])
for name in concrete:
    fields = [(t, f) for t, f in classes[name][1] if t in ("RichText", "RichText[]")]
    if not fields:
        continue
    lines.extend([f"        case TdApi.{name}.CONSTRUCTOR: {{", f"          TdApi.{name} v = (TdApi.{name}) value;"])
    for t, f in fields:
        if t == "RichText[]":
            lines.extend([
              f"          if (v.{f} != null) {{",
              f"            for (int i = 0; i < v.{f}.length; i++) {{",
              f"              if (v.{f}[i] != null) {{",
              f"                v.{f}[i] = transform.apply(v.{f}[i]);",
              "              }",
              "            }",
              "          }"
            ])
        else:
            lines.extend([
              f"          if (v.{f} != null) {{",
              f"            v.{f} = transform.apply(v.{f});",
              "          }"
            ])
    lines.extend(["          break;", "        }"])
lines.extend(["      }", "    });", "  }", "}", ""])
generated = "\n".join(lines)
if args.check:
  if DEST.read_text(encoding="utf-8") != generated:
    raise SystemExit("ArticleCodec.java is stale; run scripts/generate-article-codec.py")
else:
  DEST.write_text(generated, encoding="utf-8", newline="\n")
print(f"{'Checked' if args.check else 'Generated'} {len(concrete)} typed constructors, "
      f"schema {schema_hash}, TDLib source {source_commit}; reads legacy {LEGACY_SCHEMA}")
