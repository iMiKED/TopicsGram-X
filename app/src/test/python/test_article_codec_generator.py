# This file is a part of Telegram X. SPDX-License-Identifier: GPL-3.0-or-later
"""Offline generator checks; ARTICLE_CODEC_TDAPI_SOURCE may point at a stock model checkout."""
import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
GENERATOR = ROOT / "scripts/generate-article-codec.py"
CODEC = ROOT / "app/src/main/java/org/thunderdog/challegram/data/article/ArticleCodec.java"
SOURCE = Path(os.environ.get(
  "ARTICLE_CODEC_TDAPI_SOURCE",
  str(ROOT / "tdlib/src/main/java/org/drinkless/tdlib/TdApi.java")
))


class ArticleCodecGeneratorTest(unittest.TestCase):
  def run_generator(self, source):
    before = CODEC.read_bytes()
    result = subprocess.run(
      [sys.executable, str(GENERATOR), "--check", "--source", str(source)],
      capture_output=True,
      text=True,
      encoding="utf-8",
      check=False
    )
    self.assertEqual(before, CODEC.read_bytes(), "Check must never rewrite the codec")
    return result

  def reject_model(self, model):
    with tempfile.TemporaryDirectory(prefix="article-codec-model-") as directory:
      source = Path(directory) / "TdApi.java"
      source.write_text(model, encoding="utf-8")
      result = self.run_generator(source)
    self.assertNotEqual(0, result.returncode)
    self.assertIn("ValueError", result.stderr)
    self.assertRegex(result.stderr, "Expected stock|review compatibility first")

  def changed_constructor(self, name):
    model = SOURCE.read_text(encoding="utf-8")
    pattern = (
      r"(public static class " + name + r" extends .*?"
      r"public static final int CONSTRUCTOR = )(-?\d+)(;)"
    )
    changed, count = re.subn(
      pattern,
      lambda match: match[1] + str(int(match[2]) + 1) + match[3],
      model,
      count=1,
      flags=re.S
    )
    self.assertEqual(1, count)
    return changed

  def test_checked_in_codec_matches_stock_model(self):
    result = self.run_generator(SOURCE)
    self.assertEqual(0, result.returncode, result.stdout + result.stderr)
    self.assertIn("291 typed constructors", result.stdout)
    self.assertIn("4c6377aa7386c39217878a9a56d581922ad37ca4", result.stdout)
    self.assertIn("reads legacy af52186", result.stdout)

  def test_unexpected_token_field_is_rejected(self):
    model = SOURCE.read_text(encoding="utf-8")
    self.assertIn("public String unpaidMessagesToken;", model)
    self.reject_model(model.replace("unpaidMessagesToken", "unexpectedToken"))

  def test_unrelated_field_change_is_rejected(self):
    model = SOURCE.read_text(encoding="utf-8")
    self.assertIn("public String draftText;", model)
    self.reject_model(model.replace("public String draftText;", "public String changedDraft;"))

  def test_legacy_constructor_only_change_is_rejected(self):
    self.reject_model(self.changed_constructor("RichTextPlain"))

  def test_migrated_constructor_only_change_is_rejected(self):
    self.reject_model(self.changed_constructor("InternalLinkTypePublicChat"))

  def test_current_only_constructor_change_is_rejected(self):
    self.reject_model(self.changed_constructor("ProxyTypeWeb"))


if __name__ == "__main__":
  unittest.main()
