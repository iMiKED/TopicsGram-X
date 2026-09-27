package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;

import java.util.ArrayList;

/** A topic is not a synthetic chat: all preview and badge data belongs to this topic. */
public final class ForumTopicView extends View {
  private final Tdlib tdlib;
  private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Path tail = new Path();
  private final ComplexReceiver receiver = new ComplexReceiver(this);
  private TdApi.ForumTopic topic;
  private Text customEmoji;
  private long emojiId;
  private String preview = "", states = "", badges = "";
  private boolean hasDraft, muted;
  private final Drawable pinnedIcon, closedIcon, hiddenIcon;

  public ForumTopicView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    pinnedIcon = Drawables.get(getResources(), R.drawable.deproko_baseline_pin_16);
    closedIcon = Drawables.get(getResources(), R.drawable.baseline_lock_16);
    hiddenIcon = Drawables.get(getResources(), R.drawable.baseline_eye_off_24);
    setBackground(Theme.fillingSelector());
    setMinimumHeight(Screen.dp(84));
    setFocusable(true);
  }

  public TdApi.ForumTopic getTopic () { return topic; }

  public void setTopic (TdApi.ForumTopic topic) {
    this.topic = topic;
    TdlibSettingsManager.LocalForumDraft local = tdlib.settings().getLocalForumDraft(topic.info.chatId, topic.info.forumTopicId);
    TdApi.DraftMessage draft = local != null ? local.draft : topic.draftMessage;
    hasDraft = draft != null && draft.content instanceof TdApi.DraftMessageContentText;
    if (hasDraft) {
      preview = Lang.getString(R.string.Draft) + ": " + ((TdApi.DraftMessageContentText) draft.content).text.text;
    } else {
      preview = topic.lastMessage != null ? ContentPreview.getChatListPreview(tdlib, topic.info.chatId, topic.lastMessage, false).buildText(false) : Lang.getString(R.string.NoMessages);
    }
    ArrayList<String> status = new ArrayList<>();
    if (topic.info.isGeneral) status.add(Lang.getString(R.string.ForumGeneral));
    if (topic.isPinned) status.add(Lang.getString(R.string.ForumPinned));
    if (topic.info.isClosed) status.add(Lang.getString(R.string.ForumTopicClosed));
    if (topic.info.isHidden) status.add(Lang.getString(R.string.ForumHidden));
    states = TextUtils.join(" · ", status);
    ArrayList<String> counts = new ArrayList<>();
    if (topic.unreadCount > 0) counts.add(count(topic.unreadCount));
    if (topic.unreadMentionCount > 0) counts.add("@" + count(topic.unreadMentionCount));
    if (topic.unreadReactionCount > 0) counts.add("♥" + count(topic.unreadReactionCount));
    if (topic.unreadPollVoteCount > 0) counts.add("✓" + count(topic.unreadPollVoteCount));
    badges = TextUtils.join("  ", counts);
    muted = topic.notificationSettings != null && !topic.notificationSettings.useDefaultMuteFor ?
      topic.notificationSettings.muteFor > 0 : tdlib.chatNeedsMuteIcon(tdlib.chat(topic.info.chatId));
    long nextEmojiId = topic.info.icon != null ? topic.info.icon.customEmojiId : 0;
    if (emojiId != nextEmojiId) {
      clearEmoji();
      emojiId = nextEmojiId;
      if (emojiId != 0) {
        TdApi.FormattedText emoji = new TdApi.FormattedText("*", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeCustomEmoji(emojiId))});
        customEmoji = new Text.Builder(tdlib, emoji, null, Screen.dp(60), Paints.robotoStyleProvider(32), TextColorSets.WHITE, (text, media) -> {
          if (text == customEmoji) { text.requestMedia(receiver); invalidate(); }
        }).singleLine().build();
        customEmoji.requestMedia(receiver);
      }
    }
    setContentDescription(topic.info.name + ". " + states + ". " + preview + ". " + Lang.getString(R.string.ForumUnreadCounts, topic.unreadCount, topic.unreadMentionCount, topic.unreadReactionCount, topic.unreadPollVoteCount));
    invalidate();
  }

  private static String count (int n) { return n > 999 ? "999+" : Integer.toString(n); }

  private void text (Canvas c, String value, float start, float end, float baseline, float size, int color, boolean medium, boolean rtl) {
    paint.setTextSize(Screen.dp(size));
    paint.setTypeface(medium ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
    paint.setColor(color);
    paint.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
    String line = TextUtils.ellipsize(value.replace('\n', ' '), paint, Math.max(0, end - start), TextUtils.TruncateAt.END).toString();
    c.drawText(line, rtl ? getWidth() - start : start, Screen.dp(baseline), paint);
  }

  private float stateIcon (Canvas c, Drawable icon, float start, boolean rtl) {
    int save = c.save();
    float size = Screen.dp(16);
    c.translate(rtl ? getWidth()-start-size : start, Screen.dp(59));
    float scale = size/icon.getMinimumWidth();
    c.scale(scale, scale);
    Drawables.draw(c, icon, 0, 0, PorterDuffPaint.get(ColorId.iconLight));
    c.restoreToCount(save);
    return start+Screen.dp(20);
  }

  @Override protected void onDraw (Canvas c) {
    super.onDraw(c);
    if (topic == null) return;
    boolean rtl = Lang.rtl();
    float cx = rtl ? getWidth() - Screen.dp(34) : Screen.dp(34), cy = Screen.dp(36);
    if (customEmoji != null) {
      customEmoji.draw(c, (int) (cx - customEmoji.getWidth() / 2f), (int) (cy - customEmoji.getHeight() / 2f), null, 1f, receiver);
    } else {
      float r = Screen.dp(21);
      paint.setColor(0xff000000 | (topic.info.icon != null ? topic.info.icon.color : 0x6fb9f0));
      c.drawRoundRect(cx-r, cy-r, cx+r, cy+r-Screen.dp(3), Screen.dp(12), Screen.dp(12), paint);
      tail.reset(); tail.moveTo(cx-r+Screen.dp(3), cy+r-Screen.dp(9));
      tail.lineTo(cx-r+Screen.dp(3), cy+r+Screen.dp(2)); tail.lineTo(cx-r+Screen.dp(15), cy+r-Screen.dp(4)); tail.close();
      c.drawPath(tail, paint);
      String name = topic.info.name;
      String letter = topic.info.isGeneral || name.isEmpty() ? "#" : name.substring(0, name.offsetByCodePoints(0, 1));
      paint.setColor(0xffffffff); paint.setTextSize(Screen.dp(22)); paint.setTypeface(Fonts.getRobotoMedium()); paint.setTextAlign(Paint.Align.CENTER);
      c.drawText(letter, cx, cy-Screen.dp(2)-(paint.ascent()+paint.descent())/2, paint);
    }
    float start = Screen.dp(70), end = getWidth()-Screen.dp(14);
    text(c, topic.info.name, start, end, 25, 17, Theme.textAccentColor(), true, rtl);
    text(c, preview, start, end, 47, 14, hasDraft ? Theme.textRedColor() : Theme.textDecentColor(), false, rtl);
    float stateStart = start;
    if (topic.isPinned) stateStart = stateIcon(c, pinnedIcon, stateStart, rtl);
    if (topic.info.isClosed) stateStart = stateIcon(c, closedIcon, stateStart, rtl);
    if (topic.info.isHidden) stateStart = stateIcon(c, hiddenIcon, stateStart, rtl);
    paint.setTextSize(Screen.dp(12)); paint.setTypeface(Fonts.getRobotoMedium());
    String displayBadges = TextUtils.ellipsize(badges, paint, Math.max(0, end-stateStart-Screen.dp(20)), TextUtils.TruncateAt.END).toString();
    float badgeWidth = displayBadges.isEmpty() ? 0 : paint.measureText(displayBadges)+Screen.dp(14);
    if (badgeWidth > 0) {
      float left = rtl ? getWidth()-end : end-badgeWidth;
      paint.setColor(muted ? Theme.badgeMutedColor() : Theme.badgeColor());
      c.drawRoundRect(left, Screen.dp(56), left+badgeWidth, Screen.dp(77), Screen.dp(10), Screen.dp(10), paint);
      paint.setColor(Theme.badgeTextColor()); paint.setTextAlign(Paint.Align.CENTER);
      c.drawText(displayBadges, left+badgeWidth/2, Screen.dp(71), paint);
    }
    text(c, topic.info.isGeneral ? Lang.getString(R.string.ForumGeneral) : "", stateStart, end-badgeWidth-Screen.dp(6), 71, 12, Theme.textDecentColor(), false, rtl);
    paint.setColor(Theme.separatorColor());
    c.drawRect(rtl ? 0 : start, getHeight()-1, rtl ? getWidth()-start : getWidth(), getHeight(), paint);
  }

  private void clearEmoji () {
    if (customEmoji != null) customEmoji.performDestroy();
    customEmoji = null; emojiId = 0; receiver.clear();
  }
  public void clear () { clearEmoji(); topic = null; setContentDescription(null); }
  @Override protected void onAttachedToWindow () { super.onAttachedToWindow(); receiver.attach(); }
  @Override protected void onDetachedFromWindow () { receiver.detach(); super.onDetachedFromWindow(); }
}
