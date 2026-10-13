# Mini Apps in TopicsGram X

The client integrates the independent `upstream/mini-apps` feature, including
launch entry points, a native sheet/tab host, origin-bound WebView events,
content previews and system integrations. Product-only Russian translations
remain outside the independent feature branch.

## TDLib selection

`tdlib/mini-apps/variants.txt` selects the same Java bindings and native library
in Gradle and CMake. `lollipopArm64` and `latestArm64` use the previously built
and device-checked TDLib source `ec107f1e51da998b2d59073486295fb9e73eab54`.
All other variants, including universal APKs, retain the matching stock TDLib
source `c15d3f5a5de6e3ba5839822c451152e5e18bb700`. No variants are disabled by
this selection. BuildConfig reports the library selected for that variant.

The extended bundle implements managed-bot sharing and prevents `only_preview`
from changing recent inline-bot order. Prepared-message previews use the
standard `sendInlineQueryResultMessage` with `only_preview`; no additional
public preview API is required. Stock variants do not contain the two native
fixes. The client checks managed-bot capability before starting creation.

Native binaries and generated bindings must remain a matching pair. The CMake
resolver rejects missing or incompatible inputs. The bundle manifest records
the original build and hashes; these binaries were copied unchanged for this
integration, not rebuilt. Extending the allowlist requires building and checking
matching Java/JNI inputs for every ABI of the affected variant.

## Support and acceptance limits

- Android below 5.0 does not run Mini Apps. Launch entry points display an
  unsupported-platform explanation while the native client remains available.
  A supported WebView with the required secure messaging API is also necessary.
- Stories sharing is deferred until a native story editor and publishing flow
  exist. Resume implementation and acceptance when those prerequisites ship.
- Invoices, Stars and subscriptions are deferred until the native payment flow
  exists. Resume their integration and final payment-status checks then.
- Age verification is a privileged flow launched only for the account's
  Telegram-provided verification bot. Ordinary Mini Apps cannot change account
  restriction settings. Real acceptance requires an eligible account and bot;
  synthetic tests do not establish server acceptance.
- Prepared-message, requested-chat and managed-bot success paths require real
  server fixtures. Controlled transport results do not replace those fixtures.
- Device checks cover specific APKs, devices, WebView versions and transitions.
  They do not establish complete visual parity or coverage of every OEM,
  permission, biometric-enrollment or media-picker combination.

## Integration validation, 14 October 2026

The independent feature passed 42 JVM tests and production lint. The product
passed 645 JVM tests for each of `latestArm64Debug`, `lollipopArm64Debug` and
`legacyArm32Debug`; Java schema and BuildConfig selection were checked across
five variants, and seven actual native input pairs were resolved and hashed.
The product configuration check excludes native compilation and packaging.

Separately built integration APKs from `c4af2972` (ARM64) and `9b81122e`
(legacy ARMv7) were installed as updates. Pixel 6/API37 and Mi A2/API29 passed
380 and 381 Debug device checks respectively, followed by real Mini App smoke
checks in Debug and Release. NO1 S6/API17 passed native-client and user-confirmed
unsupported-launch checks in both variants. Those APKs predate this build-layout
commit and are not labelled as builds of a later main revision.

Detailed local reports and private device evidence are maintained in the
workspace `reports/TGMINIAPPS_COMPLETION_20261014.MD` and the six APK archives.
Publishing this integration requires making the pinned TDLib bundle commit
and its LFS objects available first. This local integration performs no push.
The product submodule targets `iMiKED/tdlib`; that bundle repository must be
created and populated before the product commit is published. The independent
Mini Apps feature continues to use the upstream bundle.
