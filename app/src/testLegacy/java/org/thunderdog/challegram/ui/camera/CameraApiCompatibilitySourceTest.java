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
 * File created on 07/11/2026
 */
package org.thunderdog.challegram.ui.camera;

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
import static org.junit.Assert.fail;

/** Source-contract regressions only: no Android camera, codec, or SDK runtime is simulated. */
@me.vkryl.annotation.MadeByLLM(value = "gpt-5", id = "camera-api-compat")
public class CameraApiCompatibilitySourceTest {
  private static final String CAMERA = "ui/camera/";
  private static final String API = CAMERA + "legacy/CameraApi.java";
  private static final String MANAGER = CAMERA + "legacy/CameraManagerLegacy.java";
  private static final String SDK_18 =
    "Build.VERSION.SDK_INT>=Build.VERSION_CODES.JELLY_BEAN_MR2";
  private static final String SDK_21 = "Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP";
  private static final String RECORDER_GUARD = "if(" + SDK_18 + "&&roundRecorder!=null)";
  private static final Pattern ROUND_CALL = Pattern.compile(
    "newRoundVideoRecorder\\(|(?:roundRecorder|recorder)\\.\\w+\\("
  );

  @Test
  public void allTwelveRoundRecorderCallsStayBehindLocalApi18Guards () throws IOException {
    String source = source(API);
    assertEquals(12, countMatches(ROUND_CALL, source));
    assertRoundCalls(source, "privatebooleanisCameraBusy()", "if(" + SDK_18 + ")", 1);
    assertRoundCalls(source, "publicfinalvoidswitchToNextCameraDevice()", RECORDER_GUARD, 1);
    assertRoundCalls(source, "publicfinalvoidopenCameraIfStillNeeded(", "if(" + SDK_18, 1);
    assertRoundCalls(source, "publicfinalvoiddestroyRoundRenderer()", RECORDER_GUARD, 1);
    assertRoundCalls(
      source,
      "protectedfinalvoidsetCameraActive(",
      "if(" + SDK_18 + "&&manager.useRoundRender())",
      5
    );
    assertRoundCalls(source, "protectedfinalvoidsetCameraActive(", RECORDER_GUARD, 1);
    assertRoundCalls(source, "publicfinalvoidstartRoundVideoCapture(", "if(" + SDK_18, 1);
    assertRoundCalls(source, "publicfinalvoidfinishOrCancelRoundVideoCapture(", RECORDER_GUARD, 1);
  }

  @Test
  public void lateOpenRequiresCurrentRecorderAndEnabledRoundMode () throws IOException {
    String method = bodyAfter(source(API), "publicfinalvoidopenCameraIfStillNeeded(");
    assertContains(
      method,
      "if(" + SDK_18 + "&&recorder!=null&&this.roundRecorder==recorder&&" +
        "isCameraActive&&!cameraOpened&&manager.useRoundRender())"
    );
    assertContains(method, "openCamera(recorder.getCameraTexture());");
  }

  @Test
  public void staleStartCompletesWithFailureInsteadOfDereferencingDestroyedRecorder ()
    throws IOException {
    String method = bodyAfter(source(API), "publicfinalvoidstartRoundVideoCapture(");
    String supported = bodyAfter(method, "if(" + SDK_18 + ")");
    assertContains(
      supported,
      "if(roundRecorder!=null&&manager.useRoundRender()&&isCameraActive)"
    );
    assertEquals(
      "roundRecorder.startCapture(delegate,key,outputPath);",
      bodyAfter(supported, "if(roundRecorder!=null")
    );
    assertContains(
      supported,
      "else{UI.post(()->delegate.onVideoRecordingFinished(key,-1,0,TimeUnit.MILLISECONDS));}"
    );
    // Switching cameras keeps the recorder but temporarily clears cameraOpened.
    assertFalse(method.contains("cameraOpened"));
  }

  @Test
  public void unsupportedRoundStartCannotQueueWorkOrInvokeApi18Delegate () throws IOException {
    String method = bodyAfter(source(API), "publicfinalvoidstartRoundVideoCapture(");
    String supported = bodyAfter(method, "if(" + SDK_18 + ")");
    assertEquals("if(" + SDK_18 + "){" + supported + "}", method);
    assertEquals(
      1,
      countMatches(Pattern.compile("delegate\\.onVideoRecordingFinished\\("), supported)
    );
    assertContains(
      supported,
      "if(!checkCameraThread()){sendMessage(ACTION_START_ROUND_CAPTURE," +
        "newObject[]{key,delegate,outputPath});return;}"
    );
    assertContains(
      bodyAfter(source(MANAGER), "publicbooleanrequestRoundVideoCapture("),
      "returnfalse;"
    );
  }

  @Test
  public void api18PlusKeepsRoundPreviewRecreationAndQueuedCapture () throws IOException {
    String source = source(API);
    String activation = bodyAfter(source, "protectedfinalvoidsetCameraActive(");
    String roundPreview = bodyAfter(activation, "if(" + SDK_18 + "&&manager.useRoundRender())");
    assertContains(
      bodyAfter(roundPreview, "if(roundRecorder==null)"),
      "roundRecorder=newRoundVideoRecorder(this,surface,mSurfaceWidth,mSurfaceHeight);"
    );
    assertContains(
      bodyAfter(roundPreview, "if(roundRecorder==null)"),
      "roundRecorder.openCamera();"
    );
    assertContains(bodyAfter(roundPreview, "else"), "roundRecorder.switchToNewCamera();");
    assertEquals(
      2,
      countMatches(Pattern.compile("roundRecorder\\.setPreviewSizes\\("), roundPreview)
    );
    assertContains(
      bodyAfter(source, "publicfinalvoidstartRoundVideoCapture("),
      "if(!checkCameraThread()){sendMessage(ACTION_START_ROUND_CAPTURE," +
        "newObject[]{key,delegate,outputPath});return;}"
    );
    assertEquals(
      "api.startRoundVideoCapture(key,delegate,outputPath);returntrue;",
      bodyAfter(
        bodyAfter(source(MANAGER), "publicbooleanrequestRoundVideoCapture("),
        "if(" + SDK_18 + "&&useRoundRender()&&api.isVideoRecordSupported())"
      )
    );
  }

  @Test
  public void finishRemainsAllowedAfterPreviewClosesButNotAfterRecorderIsDestroyed ()
    throws IOException {
    String method = bodyAfter(source(API), "publicfinalvoidfinishOrCancelRoundVideoCapture(");
    assertEquals(
      "roundRecorder.finishCapture(key,saveResult);",
      bodyAfter(method, RECORDER_GUARD)
    );
    assertFalse(method.contains("isCameraActive"));
    assertFalse(method.contains("cameraOpened"));
    assertFalse(method.contains("manager.useRoundRender()"));
  }

  @Test
  public void roundModeCannotOverrideSdkAndOrdinaryVideoStillUsesLegacyRecorder ()
    throws IOException {
    String manager = source(MANAGER);
    assertContains(
      bodyAfter(manager, "publicbooleanuseRoundRender()"),
      "return" + SDK_18 + "&&useRoundRender;"
    );
    assertContains(
      bodyAfter(manager, "publicvoidsetUseRoundRender("),
      "useRoundRender=" + SDK_18 + "&&useRoundRender;"
    );
    assertContains(
      bodyAfter(manager, "publicbooleanrequestRoundVideoCapture("),
      "if(" + SDK_18 + "&&useRoundRender()&&api.isVideoRecordSupported())"
    );
    assertContains(
      bodyAfter(manager, "publicbooleanonStartVideoCapture("),
      "if(api.isVideoRecordSupported()){api.startVideoCapture(orientation);returntrue;}"
    );
    assertContains(
      bodyAfter(source(API), "protectedfinalvoidsetCameraActive("),
      "else{success=openCamera(surface);cameraOpened=success;}"
    );
    assertContains(
      bodyAfter(source(API), "privatebooleanisCameraBusy()"),
      "else{returnisVideoCapturing;}"
    );
  }

  @Test
  public void cameraXCreationAndRecreationUseTheSameExplicitApi21Gate () throws IOException {
    String controller = source(CAMERA + "CameraController.java");
    String factory = bodyAfter(controller, "privateCameraManager<?>createCameraManager(");
    assertEquals(
      "returnnewCameraManagerX(context,this);",
      bodyAfter(factory, "if(" + SDK_21 + "&&!needLegacy())")
    );
    assertContains(factory, "else{returnnewCameraManagerLegacy(context,this);}");
    assertEquals(1, countMatches(Pattern.compile("newCameraManagerX\\("), controller));
    assertEquals(2, countMatches(Pattern.compile("manager=createCameraManager\\("), controller));
    assertContains(
      bodyAfter(controller, "privatebooleanneedLegacy()"),
      "if(forceLegacy||!Config.CAMERA_X_AVAILABLE)returntrue;"
    );
    assertContains(
      bodyAfter(controller, "privatebooleanneedLegacy()"),
      "type==Settings.CAMERA_TYPE_SYSTEM?" +
        "Settings.CAMERA_TYPE_DEFAULT==Settings.CAMERA_TYPE_LEGACY:" +
        "type==Settings.CAMERA_TYPE_LEGACY;"
    );
  }

  @Test
  public void emptyGalleryHookHasNoArtificialApi21Requirement () throws IOException {
    String gallery = source("component/attach/MediaGalleryCameraView.java");
    assertEquals("", bodyAfter(gallery, "privatevoidopenCamera()"));
    assertFalse(gallery.contains("RequiresApi"));
    assertContains(bodyAfter(gallery, "publicvoidonSurfaceTextureAvailable("), "openCamera();");
    assertContains(
      bodyAfter(gallery, "if(" + SDK_21 + ")"),
      "textureView.setSurfaceTextureListener(this);"
    );
  }

  private static void assertRoundCalls (
    String source,
    String method,
    String guard,
    int expected
  ) {
    assertEquals(expected, countMatches(ROUND_CALL, bodyAfter(bodyAfter(source, method), guard)));
  }

  private static int countMatches (Pattern pattern, String source) {
    Matcher matcher = pattern.matcher(source);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private static void assertContains (String source, String expected) {
    assertTrue("Missing source contract: " + expected, source.contains(expected));
  }

  private static String bodyAfter (String source, String marker) {
    int markerStart = source.indexOf(marker);
    assertTrue("Missing declaration/guard: " + marker, markerStart >= 0);
    int start = source.indexOf('{', markerStart + marker.length());
    assertTrue("Missing body: " + marker, start >= 0);
    int depth = 1;
    for (int index = start + 1; index < source.length(); index++) {
      char character = source.charAt(index);
      if (character == '{') {
        depth++;
      } else if (character == '}' && --depth == 0) {
        return source.substring(start + 1, index);
      }
    }
    throw new AssertionError("Unclosed body: " + marker);
  }

  private static String source (String relativePath) throws IOException {
    Path root = Paths.get("").toAbsolutePath();
    while (root != null) {
      Path file = root.resolve("app/src/main/java/org/thunderdog/challegram").resolve(relativePath);
      if (Files.isRegularFile(file)) {
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        return source.replaceAll("(?s)/\\*.*?\\*/", "")
          .replaceAll("//[^\\r\\n]*", "")
          .replaceAll("\\s+", "");
      }
      root = root.getParent();
    }
    fail("Cannot find production source: " + relativePath);
    return "";
  }
}
