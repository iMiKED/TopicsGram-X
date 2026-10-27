# Forum checks: results and coverage

Evidence snapshot: 27 October 2026. This expands the short Draft PR summary; it is a manually
compiled evidence record, not a generated `describeCheckResults` comparison. No new build or
device run was performed for this documentation-only revision.

## Source and dependency provenance

| Source | Revision |
|---|---|
| Clean topics candidate | `361a0adaafa48153dfe52fab3f05097685d2cb03` |
| Upstream base | `9aea5c14351165d99b8ef901e946e8bab99c079c` |
| Validation integration | `48c9c2fbeacaa183b8a02e68214d1fbd54f14ff0` |
| Validation TDLib bundle / source | `0b4f1e76` / `1bd4f7e7` |
| Public TDLib PR head at this snapshot | `e431324d` |

The client/resource tree is the same in clean and Validation; Validation additionally uses a
separate TDLib bundle. Results are not acceptance of stock clean dependencies or a full device
run of the current public [TDLib PR #3766](https://github.com/tdlib/td/pull/3766) head.
After the dependency is accepted and integrated upstream, repeat affected checks on the actual
accepted pins. The topics PR stays in Draft.

## Build and check results

| Check | Flavor / environment | Before | After |
|---|---|---|---|
| APK/JVM matrix | `latest`, `lollipop`, `legacy`; Windows/JDK25 | No matching stock matrix in this cycle | Six Debug/optimized Release APKs; 548/548 JVM tests per flavor, no failures, errors or skips |
| minSdk imports/declarations | Same three Debug flavors | An intermediate legacy candidate exposed an API19 charset import; not a stock Before run | Zero findings; API1 UTF-8 overloads preserve stored draft bytes and malformed-input behavior |
| Room/R8 scope | All six flavors / four build types | Temporary Validation-only registration removed | 24 combinations verified; rule once in legacy/lollipop, absent in the others; constructors present in the six APKs |
| Topic UI smoke | Pixel 6/API37 `latest`, Mi A2/API29 `lollipop`, NO1/API17 `legacy`; Debug and Release | No matching stock UI run in this cycle | Bounded list/history/profile/editor-cancel/Back and selector checks; no matching crash/ANR found in retained logs |

The [pre-packaging JVM evidence](evidence/validation-jvm-20261027.json) belongs to the APK matrix.
The separate [fresh packaged-source runs](evidence/package-jvm.json) passed on all three flavors:
**548 tests / 39 suites each, with no failures, errors or skips**. Each total comprises 547 feature
checks and the existing upstream SegmentTree test. The package contains 39 source files, including
one shared fixture; that is 38 feature suites plus the upstream suite.

These two sets of JVM runs are separate executions, not additional device acceptance. Most
feature checks reference new types absent from stock upstream. An unavailable Before result is
not an observed failure. The [instructions](README.md) explain how to reproduce the checks without
combining this package with the older external source overlay.

## Device evidence and observations

- Pixel 6/API37: modern (`latest`) Debug and Release topic smoke on Validation `48c9c2fb`.
- Mi A2/API29: Lollipop Debug and Release APKs on Android 10, **not Android 5 runtime**.
  Some early profile captures lacked header controls; settled repeats showed them. No transition
  latency was measured, and those early captures are not concealed by the smoke summary.
- NO1/API17: Legacy Debug and Release topic smoke. The author attributed its temporary load
  failure to missing connectivity. This observation is not acceptance of performance or a
  controlled network-recovery test.

Earlier recorded candidates additionally covered notification read/reply/restore/count/privacy,
cross-client reading, offline recovery, pagination and permissions. The author also confirmed
bounded TalkBack gestures and default/silent notification sounds. Those scenarios were not all
repeated in the current smoke run; do not attribute all earlier device/server results to this
candidate or to the current public TDLib PR head.

Private captures, raw logs, account data and device identifiers are not included in this package.

## Historical UI evidence

The [complete media catalog](../../ui/checks/README.md) has 22 previously accepted playback files:
5 Before and 17 After, with reused Before references, **not 17 matched pairs**. They cover chat
previews, topic list/selection, rail/Back transitions, selector placement, editor, profile/materials,
notification options and message geometry.

All recordings are from Pixel 6: Before is stock `eaef48a2`, After is Validation Release
`3817e684`. They are not current-candidate captures or performance measurements. The short PR
table selects representative clips; the catalog and [manifest](../../ui/checks/forum-media/manifest.json)
retain every clip's provenance, hashes and limitations. Only previously cropped/redacted playback
files are included, not raw recordings. No new pixel review was performed for this text revision.

## Coverage limitations

- Stock TDLib without the dependency fixes, and a full device run of the current public TDLib
  PR head, have not been accepted by these Validation results.
- API16 and Android 5 runtime remain untested. Current separate APK/JVM runs for `marshmallow`,
  `tens` and `qrs` are not claimed.
- New automated UI Before/After checks and the generated `describeCheckResults` table remain
  pending. The existing historical clips do not replace those checks.
- Current `assertDrawsWithoutAllocations`, `assertAnimatesSmoothly` and `FRAME_METRICS` runs
  in an isolated environment remain pending. No performance improvement is claimed.
- A fresh matched full lint comparison on the current base/candidate remains pending. The older
  matched legacy lint run had identical findings: 51 errors, 15 warnings and one hint, not a
  green lint result.
- Mirrored RTL, extended accessibility states, remaining IME/media-context and rights/error
  combinations, incoming-pin notifications and isolated legacy FCM cold wake were not fully
  covered. Previously waived scenarios are not marked passed.
- Upstream UI/integration/benchmark tasks replace the app and clear data. They were not run on
  signed-in personal clients; any new automated run needs an approved isolated environment.

No permanent upstream tests are added by this package. The checks and demonstrations remain
separate from the production diff.
