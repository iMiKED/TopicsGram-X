/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

// region @MadeByLLM("GPT-5")
final class MiniAppRequestedChatPolicy {
  interface Gateway {
    void send (TdApi.Function<?> function, Client.ResultHandler handler);
  }

  interface ManagerCallback {
    void onResult (TdApi.User manager);
  }

  private MiniAppRequestedChatPolicy () { }

  static void requestManagedBot (long botId, Gateway gateway, ManagerCallback callback) {
    if (botId <= 0) {
      callback.onResult(null);
      return;
    }
    // The pinned stock TDLib supports managed-bot sharing; permission is still bot-specific.
    gateway.send(new TdApi.GetUser(botId), result -> {
      TdApi.User manager = result instanceof TdApi.User ? (TdApi.User) result : null;
      boolean allowed = manager != null && manager.id == botId &&
        manager.type instanceof TdApi.UserTypeBot &&
        ((TdApi.UserTypeBot) manager.type).canManageBots;
      callback.onResult(allowed ? manager : null);
    });
  }

  // Called on the UI thread, with the operation's state at callback delivery, not at send time.
  static void dispatchResponse (
    boolean ownsOperation,
    boolean current,
    boolean sharing,
    boolean interactive,
    TdApi.Object response,
    Client.ResultHandler result,
    Runnable finish
  ) {
    if (!ownsOperation) {
      return;
    }
    if (!current || (!sharing && !interactive)) {
      finish.run();
      return;
    }
    result.onResult(response);
  }
}
// endregion
