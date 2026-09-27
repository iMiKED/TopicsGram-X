package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ForumTopicPolicy;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ForumTopicActions;
import org.thunderdog.challegram.telegram.ForumTopicStore;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Shared topic actions for the list and the history header. Never mutates a cached TDLib value. */
public final class ForumTopicUi {
  private final ViewController<?> owner;
  private final Tdlib tdlib;
  private final long chatId;
  private boolean busy;
  private ForumTopicStore.ListSession pinsSession;

  public ForumTopicUi (ViewController<?> owner, long chatId) {
    this.owner = owner;
    this.tdlib = owner.tdlib();
    this.chatId = chatId;
    owner.addDestroyListener(() -> { if (pinsSession != null) pinsSession.close(); });
  }

  private TdlibForumTopicManager.Key key (int id) { return new TdlibForumTopicManager.Key(chatId, id); }
  private TdApi.ChatMemberStatus status () { return tdlib.chatStatus(chatId); }
  private TdApi.ForumTopic topic (int id) {
    TdlibForumTopicManager.Entry entry = tdlib.topics().find(key(id));
    return entry != null ? entry.value : null;
  }
  private boolean canCreate () {
    TdApi.Chat chat = tdlib.chat(chatId);
    return chat != null && tdlib.isForum(chatId) && ForumTopicPolicy.canCreate(status(), chat.permissions);
  }
  private boolean canEdit (int id) {
    TdApi.ForumTopic topic = topic(id);
    return topic != null && ForumTopicPolicy.canEdit(status(), topic.info);
  }
  private boolean check (boolean allowed) {
    if (!allowed) error(Lang.getString(R.string.ForumActionUnavailable));
    return allowed;
  }
  private void error (String message) {
    if (!owner.isDestroyed()) owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.ForumTopicTitle)).setMessage(message).setPositiveButton(Lang.getString(R.string.OK), null));
  }
  private void run (Consumer<ForumTopicActions.Callback<TdApi.Ok>> action) {
    if (busy || owner.isDestroyed()) return;
    busy = true;
    action.accept((ok, failure) -> {
      busy = false;
      if (failure != null) error(failure.message);
      // Ok only means accepted: the store refreshes full topics and lists.
    });
  }
  private void menu (String title, List<String> labels, List<Runnable> actions) {
    if (!owner.isDestroyed() && !busy) owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(title).setItems(labels.toArray(new String[0]), (dialog, which) -> {
        if (!owner.isDestroyed() && !busy) actions.get(which).run();
      }).setNegativeButton(Lang.getString(R.string.Cancel), null));
  }
  private static void add (List<String> labels, List<Runnable> actions, int label, Runnable action) {
    labels.add(Lang.getString(label)); actions.add(action);
  }

  public void showListMenu (Runnable refresh) {
    ArrayList<String> labels = new ArrayList<>(); ArrayList<Runnable> actions = new ArrayList<>();
    if (canCreate()) add(labels, actions, R.string.ForumCreateTopic, () -> edit(0));
    add(labels, actions, R.string.ForumRefresh, refresh);
    menu(tdlib.chatTitle(chatId), labels, actions);
  }

  public void showTopicMenu (int id) {
    TdApi.ForumTopic t = topic(id);
    if (!check(t != null)) return;
    ArrayList<String> labels = new ArrayList<>(); ArrayList<Runnable> actions = new ArrayList<>();
    if (canEdit(id)) {
      add(labels, actions, R.string.ForumEditTopic, () -> edit(id));
      add(labels, actions, t.info.isClosed ? R.string.ForumReopenTopic : R.string.ForumCloseTopic, () -> {
        TdApi.ForumTopic current = topic(id);
        if (check(current != null && canEdit(id))) run(cb -> tdlib.topics().actions.setClosed(key(id), !t.info.isClosed, cb));
      });
    }
    if (ForumTopicPolicy.canManage(status())) {
      if (t.info.isGeneral) add(labels, actions, t.info.isHidden ? R.string.ForumShowGeneral : R.string.ForumHideGeneral, () -> {
        TdApi.ForumTopic current = topic(id);
        if (check(current != null && ForumTopicPolicy.canManage(status()))) run(cb -> tdlib.topics().actions.setGeneralHidden(chatId, !t.info.isHidden, cb));
      });
      add(labels, actions, t.isPinned ? R.string.ForumUnpinTopic : R.string.ForumPinTopic, () -> pin(id, !t.isPinned));
      if (t.isPinned) {
        add(labels, actions, R.string.ForumMovePinUp, () -> movePin(id, -1));
        add(labels, actions, R.string.ForumMovePinDown, () -> movePin(id, 1));
      }
    }
    add(labels, actions, R.string.ForumNotifications, () -> notifications(id));
    add(labels, actions, R.string.ForumGroupProfile, () -> tdlib.ui().openChatProfile(owner, chatId, null, null));
    if (ForumTopicPolicy.canOfferDelete(status(), t.info)) add(labels, actions,
      t.info.isGeneral ? R.string.ForumClearGeneral : R.string.ForumDeleteTopic, () -> confirmDelete(id));
    menu(t.info.name, labels, actions);
  }

  private void confirmDelete (int id) {
    TdApi.ForumTopic t = topic(id);
    if (!check(t != null && ForumTopicPolicy.canOfferDelete(status(), t.info))) return;
    owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(t.info.name).setMessage(Lang.getString(t.info.isGeneral ? R.string.ForumClearGeneralConfirm : R.string.ForumDeleteTopicConfirm))
      .setNegativeButton(Lang.getString(R.string.Cancel), null)
      .setPositiveButton(Lang.getString(R.string.Delete), (dialog, which) -> {
        TdApi.ForumTopic current = topic(id);
        if (check(current != null && ForumTopicPolicy.canOfferDelete(status(), current.info))) {
          // Creator exception (<=11 messages, all their own) is checked by Telegram, not guessed from a partial history page.
          run(cb -> tdlib.topics().actions.delete(key(id), cb));
        }
      }));
  }

  private void withPins (Consumer<List<TdApi.ForumTopic>> action) {
    if (!check(ForumTopicPolicy.canManage(status())) || busy) return;
    busy = true;
    pinsSession = tdlib.topics().openList(chatId, "", value -> {
      if (owner.isDestroyed() || pinsSession == null) return;
      if (value.error != null) {
        pinsSession.close(); pinsSession = null; busy = false; error(value.error.message);
      } else if (value.initialized && !value.refreshing && !value.stale) {
        if (!ForumTopicPolicy.completePinnedPrefix(value.topics, value.endReached)) { pinsSession.loadMore(); return; }
        pinsSession.close(); pinsSession = null; busy = false;
        if (check(ForumTopicPolicy.canManage(status()))) action.accept(value.topics);
      }
    });
    pinsSession.refresh();
  }

  private void pin (int id, boolean pinnedState) {
    withPins(topics -> {
      TdApi.ForumTopic current = topic(id);
      if (!check(current != null)) return;
      int pinned = 0;
      for (TdApi.ForumTopic t : topics) if (t.isPinned) pinned++;
      if (pinnedState && !current.isPinned && pinned >= tdlib.options().pinnedForumTopicCountMax) {
        error(Lang.getString(R.string.ForumPinLimit, tdlib.options().pinnedForumTopicCountMax)); return;
      }
      run(cb -> tdlib.topics().actions.setPinned(key(id), pinnedState, cb));
    });
  }
  private void movePin (int id, int direction) {
    withPins(topics -> {
      int[] order = ForumTopicPolicy.movePin(topics, id, direction);
      if (order == null) { error(Lang.getString(R.string.ForumPinAtEdge)); return; }
      run(cb -> tdlib.topics().actions.setPinnedOrder(chatId, order, cb));
    });
  }

  private void notifications (int id) {
    String[] labels = {Lang.getString(R.string.ForumNotificationsDefault), Lang.getString(R.string.ForumNotificationsOn),
      Lang.getString(R.string.ForumMuteHour), Lang.getString(R.string.ForumMuteDay), Lang.getString(R.string.ForumMuteForever)};
    int[] durations = {0, 0, 3600, 86400, Integer.MAX_VALUE};
    TdApi.ForumTopic t = topic(id);
    if (!check(t != null && t.notificationSettings != null)) return;
    String state = Lang.getString(t.notificationSettings.useDefaultMuteFor ? R.string.ForumNotificationsDefault :
      t.notificationSettings.muteFor > 0 ? R.string.ForumNotificationsMuted : R.string.ForumNotificationsOn);
    owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.ForumNotifications) + " · " + state)
      .setItems(labels, (dialog, which) -> {
        TdApi.ForumTopic current = topic(id);
        if (check(current != null && current.notificationSettings != null)) run(cb -> tdlib.topics().actions.setNotifications(key(id),
          ForumTopicPolicy.notifications(current.notificationSettings, which == 0, durations[which]), cb));
      }).setNegativeButton(Lang.getString(R.string.Cancel), null));
  }

  private LinearLayout column () {
    LinearLayout view = new LinearLayout(owner.context());
    view.setOrientation(LinearLayout.VERTICAL);
    int pad = Screen.dp(16); view.setPadding(pad, pad, pad, pad);
    return view;
  }

  private void edit (int id) {
    boolean creating = id == 0;
    if (!check(creating ? canCreate() : canEdit(id))) return;
    TdApi.ForumTopic initial = creating ? null : topic(id);
    int[] color = {creating ? ForumTopicPolicy.ICON_COLORS[0] : initial.info.icon.color};
    long[] emoji = {creating ? 0 : initial.info.icon.customEmojiId};
    long initialEmoji = emoji[0];
    LinearLayout content = column();
    EditText name = new EditText(owner.context());
    name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    name.setSingleLine(true); name.setTextColor(Theme.textAccentColor());
    name.setHint(Lang.getString(R.string.ForumTopicName));
    name.setText(creating ? "" : initial.info.name);
    content.addView(name);
    if (creating) {
      LinearLayout colors = new LinearLayout(owner.context());
      int[] colorLabels = {R.string.ForumColorBlue, R.string.ForumColorYellow, R.string.ForumColorPurple, R.string.ForumColorGreen, R.string.ForumColorPink, R.string.ForumColorRed};
      for (int i = 0; i < ForumTopicPolicy.ICON_COLORS.length; i++) {
        int value = ForumTopicPolicy.ICON_COLORS[i];
        Button button = new Button(owner.context());
        button.setText(value == color[0] ? "●" : "○"); button.setTextSize(24); button.setTextColor(0xff000000 | value);
        button.setContentDescription(Lang.getString(colorLabels[i])); button.setSelected(value == color[0]);
        button.setOnClickListener(v -> {
          color[0] = value;
          for (int j = 0; j < colors.getChildCount(); j++) {
            Button b = (Button) colors.getChildAt(j); b.setSelected(b == v); b.setText(b == v ? "●" : "○");
          }
        });
        colors.addView(button, new LinearLayout.LayoutParams(0, Screen.dp(52), 1));
      }
      content.addView(colors);
    }
    if (creating || !initial.info.isGeneral) {
      Button icon = new Button(owner.context());
      icon.setText(Lang.getString(emoji[0] == 0 ? R.string.ForumChooseIcon : R.string.ForumCustomIconSelected));
      icon.setOnClickListener(v -> chooseIcon(value -> {
        emoji[0] = value;
        icon.setText(Lang.getString(value == 0 ? R.string.ForumChooseIcon : R.string.ForumCustomIconSelected));
      }));
      content.addView(icon);
    }
    TextView error = new TextView(owner.context()); error.setTextColor(Theme.getColor(ColorId.textNegative)); content.addView(error);
    ScrollView scroll = new ScrollView(owner.context()); scroll.addView(content);
    AlertDialog dialog = owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(creating ? R.string.ForumCreateTopic : R.string.ForumEditTopic)).setView(scroll)
      .setPositiveButton(Lang.getString(R.string.Save), null).setNegativeButton(Lang.getString(R.string.Cancel), null));
    if (dialog == null) return;
    Button save = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
    save.setOnClickListener(v -> {
      String text = name.getText().toString().trim();
      if (!ForumTopicPolicy.validName(text)) { error.setText(Lang.getString(R.string.ForumInvalidName)); return; }
      if (!(creating ? canCreate() : canEdit(id))) { error.setText(Lang.getString(R.string.ForumActionUnavailable)); return; }
      if (busy) return;
      busy = true; save.setEnabled(false); error.setText(Lang.getString(R.string.ForumSaving));
      Consumer<TdApi.Error> done = failure -> {
        busy = false;
        if (owner.isDestroyed() || !dialog.isShowing()) return;
        save.setEnabled(true);
        if (failure != null) error.setText(failure.message); else dialog.dismiss();
      };
      if (creating) tdlib.topics().actions.create(chatId, text, false, new TdApi.ForumTopicIcon(color[0], emoji[0]), (value, failure) -> {
        boolean stillEditing = dialog.isShowing();
        done.accept(failure);
        if (value != null && stillEditing && !owner.isDestroyed()) tdlib.ui().openChat(owner, chatId,
          new TdlibUi.ChatOpenParameters().messageTopic(new TdApi.MessageTopicForum(value.forumTopicId)).keepStack());
      });
      else tdlib.topics().actions.edit(key(id), text, !initial.info.isGeneral && emoji[0] != initialEmoji, emoji[0], (value, failure) -> done.accept(failure));
    });
  }

  private void chooseIcon (Consumer<Long> selected) {
    tdlib.send(new TdApi.GetForumTopicDefaultIcons(), (stickers, failure) -> tdlib.ui().post(() -> {
      if (owner.isDestroyed()) return;
      if (failure != null) { error(failure.message); return; }
      LinearLayout content = column();
      ArrayList<CustomTextView> cells = new ArrayList<>();
      AlertDialog[] popup = new AlertDialog[1];
      Consumer<Long> choose = value -> { selected.accept(value); popup[0].dismiss(); };
      Button regular = new Button(owner.context()); regular.setText(Lang.getString(R.string.ForumRegularIcon));
      regular.setOnClickListener(v -> choose.accept(0L)); content.addView(regular);
      GridLayout grid = new GridLayout(owner.context()); grid.setColumnCount(4);
      for (TdApi.Sticker sticker : stickers.stickers) {
        if (!(sticker.fullType instanceof TdApi.StickerFullTypeCustomEmoji)) continue;
        long emojiId = ((TdApi.StickerFullTypeCustomEmoji) sticker.fullType).customEmojiId;
        CustomTextView cell = new CustomTextView(owner.context(), tdlib);
        cell.setTextSize(32); cell.setTextColorId(ColorId.text); cell.setPadding(Screen.dp(10), Screen.dp(8), 0, 0);
        TdApi.FormattedText formatted = new TdApi.FormattedText("*", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeCustomEmoji(emojiId))});
        cell.setText(formatted.text, TextEntity.valueOf(tdlib, formatted, null), false);
        cell.setContentDescription(sticker.emoji); cell.setFocusable(true);
        cell.setOnClickListener(v -> choose.accept(emojiId));
        // This is a selector, not a link to the emoji's sticker set.
        float[] down = new float[2]; boolean[] pressed = new boolean[1];
        cell.setOnTouchListener((v, event) -> {
          if (event.getAction() == MotionEvent.ACTION_DOWN) { down[0] = event.getX(); down[1] = event.getY(); pressed[0] = true; }
          else if (event.getAction() == MotionEvent.ACTION_MOVE && (Math.abs(event.getX()-down[0]) > Screen.getTouchSlop() || Math.abs(event.getY()-down[1]) > Screen.getTouchSlop())) pressed[0] = false;
          else if (event.getAction() == MotionEvent.ACTION_CANCEL) pressed[0] = false;
          else if (event.getAction() == MotionEvent.ACTION_UP) { if (pressed[0]) v.performClick(); pressed[0] = false; }
          return true;
        });
        grid.addView(cell, new ViewGroup.LayoutParams(Screen.dp(60), Screen.dp(56))); cells.add(cell);
      }
      content.addView(grid);
      if (tdlib.hasPremium()) {
        Button more = new Button(owner.context()); more.setText(Lang.getString(R.string.ForumMoreIcons));
        more.setOnClickListener(v -> { popup[0].dismiss(); customIcon(selected); }); content.addView(more);
      }
      ScrollView scroll = new ScrollView(owner.context()); scroll.addView(content);
      popup[0] = owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme()).setTitle(Lang.getString(R.string.ForumChooseIcon))
        .setView(scroll).setNegativeButton(Lang.getString(R.string.Cancel), null));
      if (popup[0] != null) popup[0].setOnDismissListener(d -> { for (CustomTextView cell : cells) cell.performDestroy(); });
    }));
  }

  private void customIcon (Consumer<Long> selected) {
    if (!tdlib.hasPremium()) return;
    EmojiLayout layout = new EmojiLayout(owner.context());
    PopupLayout[] popup = new PopupLayout[1];
    layout.initWithEmojiStatus(owner, new EmojiLayout.Listener() {
      @Override public boolean onSetEmojiStatus (View view, TGStickerObj sticker, TdApi.EmojiStatus status) {
        if (!tdlib.hasPremium() || owner.isDestroyed()) return false;
        selected.accept(sticker.getCustomEmojiId()); popup[0].hideWindow(true); return true;
      }
    }, owner);
    int height = Math.min(Screen.dp(360), owner.context().getResources().getDisplayMetrics().heightPixels / 2);
    popup[0] = owner.showPopup(Lang.getString(R.string.ForumChooseIcon), true, 1, (window, content) -> {
      content.addView(layout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)); return height;
    }, null);
    popup[0].setDismissListener(window -> layout.destroy());
  }
}
