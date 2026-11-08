/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Source wiring contracts. Real media loading and rendering require the Android device check. */
public class PageBlockButtonsMediaTest {
  private static String source (String relative) throws Exception {
    String root = System.getProperty("article.buttons.sourceRoot", "");
    Path directory = Paths.get(root).toAbsolutePath();
    while (directory != null) {
      Path path = directory.resolve("app/src/main/java/org/thunderdog/challegram/" + relative);
      if (Files.isRegularFile(path)) {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
          .replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "").replaceAll("\\s+", "");
      }
      directory = directory.getParent();
    }
    throw new AssertionError("Run from the product checkout: " + relative);
  }

  @Test public void everyRichLabelHasAMediaListenerBeforeMeasurement () throws Exception {
    String block = source("data/PageBlockButtons.java");
    assertTrue(block.contains("implementsTextWrapper.TextMediaListener,Destroyable"));
    assertTrue(block.contains("for(inti=0;i<labels.length;i++){labels[i]=TextWrapper.parseRichText(" +
      "context,null,newTdApi.RichTextButton(block.buttons[i]),Paints.robotoStyleProvider(15f)," +
      "TextColorSets.InstantView.NORMAL,null,this);labels[i].setViewProvider(currentViews);}"));
  }

  @Test public void loadedMediaUpdatesOnlyViewsStillBoundToThisRow () throws Exception {
    String block = source("data/PageBlockButtons.java");
    assertTrue(block.contains("for(Viewview:currentViews){if(viewinstanceofPageBlockView&&" +
      "((PageBlockView)view).getBlock()==this){PageBlockViewblockView=(PageBlockView)view;" +
      "if(!text.invalidateMediaContent(blockView.getIconReceiver(),specificMedia)){" +
      "blockView.invalidateIconsContent(this);}}}"));
  }

  @Test public void fullReloadPreservesSeparateMediaRangesForAllLabels () throws Exception {
    String block = source("data/PageBlockButtons.java");
    assertTrue(block.contains("requestIcons(ComplexReceiverreceiver){intoffset=0;" +
      "for(TextWrapperlabel:labels){intcount=label.getMaxMediaCount();" +
      "label.requestMedia(receiver,offset,count);offset+=count;}" +
      "receiver.clearReceiversWithHigherKey(offset);}"));
  }

  @Test public void destroyedRowsReleaseAllLabelMedia () throws Exception {
    assertTrue(source("data/PageBlockButtons.java").contains(
      "performDestroy(){for(TextWrapperlabel:labels){label.performDestroy();}}"));
    assertTrue(source("data/TGMessageArticle.java").contains(
      "if(blockinstanceofme.vkryl.core.lambda.Destroyable)((me.vkryl.core.lambda.Destroyable)block).performDestroy();"));
  }

  @Test public void textMediaContractIsNotWeakenedToHideTheCrash () throws Exception {
    assertTrue(source("util/text/TextWrapper.java").contains(
      "if(textMediaListener==null)thrownewIllegalStateException();text.notifyMediaChanged(null);"));
  }
}
