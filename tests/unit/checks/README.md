# Forum topic checks

Checks package for `checks/upstream/forum-topics-clean`, one commit above clean candidate
`361a0adaafa48153dfe52fab3f05097685d2cb03`. Nothing in this directory belongs in the production diff.
Keep the topics PR in Draft while
[TDLib #3766](https://github.com/tdlib/td/pull/3766) and final integration remain outstanding.

The author authorized publication of this checks package on 27 October 2026. Evidence JSON and
media manifests retain their preparation-time state; they are historical records, not live status.

See [results and coverage](RESULTS.md) for the detailed build/device evidence and limitations
behind the short PR summary. Run instructions follow below.

## Contents and provenance

The package carries 38 existing regression-source files and the four-test upstream-migration
check. One source is a shared fixture; these produce 38 feature suites, not 39 independent suites.
The existing upstream SegmentTree test accounts for the remaining suite in the 548-test runs.
Assertions and executable logic are preserved; the older sources receive the author-confirmed
`gpt-6-astra` provenance annotation and LF normalization. The existing migration check is copied
byte-for-byte. Source digests and transformations are in [source-manifest.json](evidence/source-manifest.json).
They remain change-specific checks, not newly accepted permanent upstream tests.

Coverage includes topic identity and send routing, drafts and UTF-8 compatibility, read targets,
store pagination/update ordering, unread reconciliation, editor/selection policy, preview lifecycle,
profile-link state, media classification, message layout and selector state. Fake clients and
synthetic IDs/strings do not prove server delivery, device rendering or native TDLib behavior.
Example links use the reserved `example.invalid` domain.

## Run without an account or device

Configure the repository according to its README. After restoring the checks on a **separate
checkout of the clean candidate**, run from the repository root:

```sh
./gradlew :app:testLatestDebugUnitTest -Pabi=arm64 --init-script tests/unit/checks/forum-checks.init.gradle
./gradlew :app:testLollipopDebugUnitTest -Pabi=arm64 --init-script tests/unit/checks/forum-checks.init.gradle
./gradlew :app:testLegacyDebugUnitTest -Pabi=arm -PuseLegacyNdk=true --init-script tests/unit/checks/forum-checks.init.gradle
```

Use `gradlew.bat` on Windows. Upstream already registers Kotlin checks; the small init script
registers their Java siblings only. Do not combine it with an older external test-source overlay:
the copied classes would be duplicated. Additional affected flavors are `marshmallow`, `tens`,
and `qrs`; their current APK/JVM runs have not been claimed.

Most checks reference new feature types and cannot compile on stock upstream. Their Before value
is **not run**, not an observed failure. Use an isolated checkout for a meaningful base comparison
and follow `tests/README.md`; do not stash/reset the user's product checkout. Existing reference
results are under [evidence](evidence/validation-jvm-20261027.json). They ran on Validation
`48c9c2fb`, with the separate TDLib integration bundle, before this package was assembled.
Fresh verification of these packaged sources passed on all three flavors: 548 tests in 39 suites
per flavor, with no failures, errors or skips. The [packaging results](evidence/package-jvm.json)
are separate from the prior APK matrix and are not new device or stock-native TDLib acceptance.

## Result boundaries

The historical 548/548 totals include the upstream SegmentTree test and 547 feature checks.
No generated `describeCheckResults` Before/After table is fabricated. A matching base run,
new automated UI checks, `assertDrawsWithoutAllocations`, `assertAnimatesSmoothly` and
`FRAME_METRICS` remain pending. Do not run upstream UI/integration/benchmark tasks on signed-in
personal clients: those tasks replace the application and clear its data. Use an explicitly
approved isolated environment and test accounts instead.

No APK, native binary, local configuration, credentials, session snapshot, raw device log or
private screenshot is included. The only media are the historical, previously accepted files in
[the separate media catalog](../../ui/checks/README.md). These are not current-candidate captures.

The preparation and packaging are LLM-assisted. The author's earlier manual code review is not
an independent review of this checks package or the latest upstream changes.
