# Upstream integration and product verification — 2026-10-07

Product build source: `1150357b4ea89ce241a35ae7d06c24b027d6f19d`.
Upstream base: `7e3e3a3b1d658addd10514e2efd97de6e3ba1a42`.
Documentation-only commits may follow these tested sources.

The product includes the overlapping-topic-pagination correction
`ac5f556c28f64479b49550d8ab45327066ad4e1a` and article changes through
`e9a48987fc7729546dc7ea37fff7de4d75567724`, including their forum integration.
The independent article PR additionally normalizes contribution resources and
documents its own validation; product-only translations remain in this branch.

## Native update

Upstream now selects TDLib/OpenSSL binaries by NDK, Android platform and ABI.
The product merge preserves its strict TDLib Java/native compatibility resolver
and adds the platform to that selection. Wrong-platform and unscoped binaries
are not silently reused. All 12 resolver fixtures passed.

- TDLib module: `3b99c64a56d1392f7dcf0a246121c1677db8208b`.
- LevelDB module: `9fc37657b10e64749711fd135cffc35eacb13229`.
- TDLib Java/native version: `42e6a5259551178d1dab54a22ad96d14bd906e20`.
- NDK: `30.0.16248370`; modern builds select `android-24/arm64-v8a` inputs.

The product native targets were actually rebuilt for both Debug and Release;
this was not a native-reuse-only validation. Recursive submodule checkouts
match their pins. Repository text is LF, with the intended CRLF checkout for
the Windows Gradle launcher.

One upstream optimization remains inactive in this local toolchain:
`CMAKE_SYSTEM_VERSION` is set to `1` by the legacy Android CMake toolchain,
despite `ANDROID_PLATFORM=android-24`. Consequently upstream's new
`CMAKE_SYSTEM_VERSION >= 23` packed-relocation condition does not enable
`--pack-dyn-relocs=android` for the locally built `tgxjni`/`tgcallsjni` targets.
Their ELF files contain ordinary RELA relocations. This does not prevent
installation or startup, and does not change the application's minSdk.
The upstream condition is retained unchanged; packed-relocation optimization
of those targets is not claimed by these checks.

## Results

| Check | Result |
|---|---|
| Product JVM regression suite | 585/585 passed, 44 suites |
| TDLib resolver fixtures | 12/12 passed |
| Modern Debug build | Passed, 19m33s |
| Modern Release, R8 and resource shrinking | Passed, 17m11s |
| Production-source Debug/Release lint | No new issues; 17 unchanged baseline-filtered warnings |
| APK signature and package isolation | Existing v2/v3 certificate; separate Debug/Release packages |
| Packaged native provenance | All 11 libraries match their build outputs; platform-specific TDLib/OpenSSL inputs checked |
| Release alignment | ZIP and all 11 ELF libraries pass 16 KiB alignment checks |
| Source provenance | Both APKs embed the exact product build commit above |
| Modern device update/startup | Both updated in place; identity/data preserved; authenticated chat list displayed; no current-process startup fatal |

Debug SHA-256:
`8E32E3F5931665E8410EC4CD3B463A8A5513127444F4CE0F977E56E2FA1C3345`.

Release SHA-256:
`B3512D40227E3F56F6D239C10068180B8CE80FA910A2191C1912DE020E129CB2`.
The fresh R8 mapping was archived and checked against the original file.

The Windows test harness excludes test-source UAST from lint because of a
local analysis stall; executable JVM tests remain enabled. These results are
not a new full forum/article server acceptance matrix, notification-delivery
test, legacy/all-ABI run, or test on a 16 KiB-page device. The standalone article
branch has separate unit/lint and isolated renderer evidence; product runtime
checks do not substitute for its normal application acceptance.

No real chat content, private configuration, build harness, device identifiers
or binary distribution artifacts are included in the source publication.
