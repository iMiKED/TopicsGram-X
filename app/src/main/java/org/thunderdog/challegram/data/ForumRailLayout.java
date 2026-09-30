package org.thunderdog.challegram.data;

/** Geometry shared by measurement, hit testing and the navigation gesture boundary. */
public final class ForumRailLayout {
  private ForumRailLayout () { }

  public static int widthDp (float viewportDp) {
    return viewportDp >= 600 ? 72 : viewportDp < 360 ? 56 : 64;
  }

  public static int occupied (int railWidth, float reveal) {
    return Math.round(railWidth * Math.max(0f, Math.min(1f, reveal)));
  }

  public static boolean hitRail (int viewportWidth, int occupied, float x, boolean rtl) {
    return occupied > 0 && x >= 0 && x < viewportWidth && (rtl ? x >= viewportWidth - occupied : x < occupied);
  }

  public static float contentX (int occupied, float x, boolean rtl) {
    return rtl ? x : x - occupied;
  }
}
