/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

// region @MadeByLLM("GPT-5")
public class MiniAppRequestedChatPolicyTest {
  private static final long BOT_ID = 42;
  private static final String APP = "https://miniapp.example/";

  @Test
  public void stockTdlibNeedsOnlyGetUserWithoutCustomOption () {
    RequestHarness harness = new RequestHarness();
    harness.requestManager(BOT_ID);
    assertEquals(1, harness.requests.size());
    assertTrue(harness.requests.get(0) instanceof TdApi.GetUser);
    assertEquals(BOT_ID, ((TdApi.GetUser) harness.requests.get(0)).userId);

    TdApi.User manager = manager(BOT_ID, true);
    harness.respond(manager);
    assertEquals(1, harness.managers.size());
    assertSame(manager, harness.managers.get(0));
    assertEquals(1, harness.requests.size());
    assertEquals(0, harness.failures);
  }

  @Test
  public void managerMustHaveCanManageBotsPermission () {
    TdApi.User manager = manager(BOT_ID, false);
    assertRejected(manager);
    assertFalse(((TdApi.UserTypeBot) manager.type).canManageBots);
  }

  @Test
  public void managerMustBeTheRequestedBot () {
    assertRejected(manager(BOT_ID + 1, true));
    assertRejected(manager(0, true));
    assertRejected(manager(-1, true));
  }

  @Test
  public void invalidBotIdDoesNotSendARequest () {
    for (long botId : new long[] {0, -1}) {
      RequestHarness harness = new RequestHarness();
      harness.requestManager(botId);
      assertTrue(harness.requests.isEmpty());
      assertTrue(harness.managers.isEmpty());
      assertEquals(1, harness.failures);
    }
  }

  @Test
  public void nonBotAndMissingUserTypesAreRejected () {
    TdApi.UserType[] types = {
      null,
      new TdApi.UserTypeRegular(),
      new TdApi.UserTypeDeleted(),
      new TdApi.UserTypeUnknown()
    };
    for (TdApi.UserType type : types) {
      TdApi.User user = manager(BOT_ID, true);
      user.type = type;
      assertRejected(user);
    }
  }

  @Test
  public void errorsAndUnexpectedResponsesCannotGrantPermissionOrTriggerFallback () {
    TdApi.Object[] responses = {
      null,
      new TdApi.Error(400, "USER_NOT_FOUND"),
      new TdApi.Error(500, "Internal Server Error"),
      new TdApi.Ok(),
      new TdApi.OptionValueEmpty(),
      new TdApi.OptionValueBoolean(false),
      new TdApi.OptionValueBoolean(true),
      new TdApi.Chat()
    };
    for (TdApi.Object response : responses) {
      assertRejected(response);
    }
  }

  @Test
  public void canceledRequestIgnoresLateSuccessAndFailure () {
    RequestHarness harness = new RequestHarness();
    harness.requestManager(BOT_ID);
    harness.finish();
    harness.respond(manager(BOT_ID, true));
    harness.respond(new TdApi.Error(400, "CANCELED"));
    assertTrue(harness.managers.isEmpty());
    assertEquals(1, harness.failures);
    assertEquals(1, harness.requests.size());
  }

  @Test
  public void navigationRejectsStaleManagerAndFinishesOnlyOnce () {
    RequestHarness harness = new RequestHarness();
    harness.requestManager(BOT_ID);
    harness.document.navigate();
    harness.respond(manager(BOT_ID, true));
    harness.respond(manager(BOT_ID, true));
    assertTrue(harness.managers.isEmpty());
    assertEquals(1, harness.failures);
  }

  @Test
  public void closedDocumentRejectsLateManager () {
    RequestHarness harness = new RequestHarness();
    harness.requestManager(BOT_ID);
    harness.document.close();
    harness.respond(manager(BOT_ID, true));
    assertTrue(harness.managers.isEmpty());
    assertEquals(1, harness.failures);
  }

  @Test
  public void oldCallbackCannotFinishAReplacementOperation () {
    RequestHarness harness = new RequestHarness();
    harness.requestManager(BOT_ID);
    harness.ownsOperation = false;
    harness.respond(manager(BOT_ID, true));
    harness.respond(new TdApi.Error(400, "OLD_REQUEST"));
    assertTrue(harness.managers.isEmpty());
    assertEquals(0, harness.failures);
  }

  @Test
  public void hiddenUiCannotStartCreatingAManagedBot () {
    RequestHarness harness = new RequestHarness();
    harness.requestManager(BOT_ID);
    harness.interactive = false;
    harness.respond(manager(BOT_ID, true));
    assertTrue(harness.managers.isEmpty());
    assertEquals(1, harness.failures);
  }

  @Test
  public void submittedShareCanCompleteWhileHiddenOnlyForCurrentDocument () {
    for (boolean navigate : new boolean[] {false, true}) {
      RequestHarness harness = new RequestHarness();
      List<TdApi.Object> replies = new ArrayList<>();
      harness.send(
        new TdApi.ShareUsersWithBot(
          new TdApi.KeyboardButtonSourceWebApp(BOT_ID, "prepared-request"),
          7,
          new long[] {99},
          false
        ),
        replies::add
      );
      harness.sharing = true;
      harness.interactive = false;
      if (navigate) {
        harness.document.navigate();
      }
      TdApi.Ok response = new TdApi.Ok();
      harness.respond(response);
      assertEquals(navigate ? 0 : 1, replies.size());
      assertEquals(navigate ? 1 : 0, harness.failures);
      if (!navigate) {
        assertSame(response, replies.get(0));
      }
    }
  }

  @Test
  public void callbackGateRequiresOwnershipAndCurrentDocumentForEveryState () {
    for (int state = 0; state < 16; state++) {
      boolean ownsOperation = (state & 1) != 0;
      boolean current = (state & 2) != 0;
      boolean sharing = (state & 4) != 0;
      boolean interactive = (state & 8) != 0;
      List<TdApi.Object> replies = new ArrayList<>();
      List<Boolean> failures = new ArrayList<>();
      TdApi.Ok response = new TdApi.Ok();
      MiniAppRequestedChatPolicy.dispatchResponse(
        ownsOperation,
        current,
        sharing,
        interactive,
        response,
        replies::add,
        () -> failures.add(false)
      );
      boolean accepted = ownsOperation && current && (sharing || interactive);
      assertEquals("state " + state, accepted ? 1 : 0, replies.size());
      assertEquals("state " + state, ownsOperation && !accepted ? 1 : 0, failures.size());
    }
  }

  private static TdApi.User manager (long userId, boolean canManageBots) {
    TdApi.UserTypeBot type = new TdApi.UserTypeBot();
    type.canManageBots = canManageBots;
    TdApi.User user = new TdApi.User();
    user.id = userId;
    user.type = type;
    return user;
  }

  private static void assertRejected (TdApi.Object response) {
    RequestHarness harness = new RequestHarness();
    harness.requestManager(BOT_ID);
    harness.respond(response);
    assertTrue(harness.managers.isEmpty());
    assertEquals(1, harness.failures);
    assertEquals(1, harness.requests.size());
    harness.respond(manager(BOT_ID, true));
    assertTrue(harness.managers.isEmpty());
    assertEquals(1, harness.failures);
  }

  private static final class RequestHarness {
    final MiniAppDocument document = new MiniAppDocument(APP, true);
    final MiniAppDocument.Request origin = document.receive(APP, APP, 1);
    final List<TdApi.Function<?>> requests = new ArrayList<>();
    final List<TdApi.User> managers = new ArrayList<>();
    Client.ResultHandler pending;
    boolean ownsOperation = true;
    boolean interactive = true;
    boolean sharing;
    int failures;

    void requestManager (long botId) {
      MiniAppRequestedChatPolicy.requestManagedBot(botId, this::send, manager -> {
        if (manager == null) {
          finish();
        } else {
          managers.add(manager);
        }
      });
    }

    void send (TdApi.Function<?> function, Client.ResultHandler callback) {
      requests.add(function);
      pending = response -> MiniAppRequestedChatPolicy.dispatchResponse(
        ownsOperation,
        document.isCurrent(origin, APP),
        sharing,
        interactive,
        response,
        callback,
        this::finish
      );
    }

    void respond (TdApi.Object response) {
      pending.onResult(response);
    }

    void finish () {
      failures++;
      ownsOperation = false;
    }
  }
}
// endregion
