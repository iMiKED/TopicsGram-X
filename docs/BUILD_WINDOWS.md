# Windows native build support

Upstream now supports Windows hosts for libvpx, FFmpeg and Opus preparation. This fork uses that implementation and the standard `msys2.dir` setting, plus the source-copy/version/log safeguards described below. Use the Windows JDK and Android SDK/NDK, plus MSYS2 for POSIX configure/make scripts.

The integration baseline is upstream `51a2ba25` (2026-10-06), version 1816 with NDK r30 and updated tgcalls/WebRTC. It also retains the October 5 FFmpeg, multidex startup and OpenGL intro fixes. Earlier dated results below are historical, not validation of the newest native inputs. The fork's product/build branch is now `main`.

Build verification on 2026-09-27/28 used app revision `9291ce110ccc852c0b7043b92f4ac19c4e651242` plus this branch's build changes: `assembleLatestArm64Debug`, APK metadata/signature checks, and repeat incremental builds with unchanged APK SHA-256. All 17 build-infrastructure tests passed, including the real MSYS2 argument test. These results cover ARM64 debug builds only, not other ABIs/flavors or release signing. Device test data and credentials are not part of this repository.

Verification used JDK 21.0.10, Gradle 9.7.1, AGP 9.4.0, Windows NDK 27.3.13750724, CMake 3.22.1, and MSYS2 Bash 5.3.020 / Make 4.4.1 / Perl 5.42.3 / diffutils 3.12.

## Requirements

- JDK and Android packages matching the current project configuration. Use the checked-in Gradle wrapper.
- A Windows Android SDK with the matching Build Tools, NDK and CMake packages. The platform selected by `compileSdk` is required, not just the platform named by the setup script. For the current configuration these are `android-37.0` and `android-37.2`, respectively.
- [MSYS2](https://www.msys2.org/docs/installer/) with `bash`, `make`, `perl` and `diffutils`. In the MSYS2 terminal, install missing packages with `pacman -S --needed make perl diffutils`.
- Prefer checkout and SDK paths without whitespace: third-party configure/make scripts have their own path restrictions.

Git for Windows includes Bash and Perl, but does not normally include the MSYS GNU Make needed for the full native build. The NDK's Windows Make is not a replacement for MSYS Make in these tasks. WSL's `bash.exe` is not used.

## Configuration and build

Configure the normal ignored `local.properties`, including `sdk.dir` pointing to the Windows SDK and the Telegram credentials. Do not commit credentials or pass them as command-line arguments.

Set the MSYS2 installation in ignored `local.properties`, using forward slashes and a path without whitespace:

```properties
msys2.dir=C:/Tools/msys64
```

The old `TGX_MSYS2_ROOT` environment variable is no longer needed to select tools for Gradle tasks; explicit `msys2.dir` takes precedence. It remains an optional input for the standalone host-helper runtime tests.

```powershell
./gradlew.bat :app:assembleLatestArm64Debug
```

This is an ARM64 debug build for the latest SDK flavor, not a verification of every ABI/flavor. A production release additionally requires the project's normal signing and service configuration.

The upstream helpers pass configure scripts directly to MSYS2 Bash, without a `bash -c` command string. They invoke MSYS Make/Perl and Windows NDK `.exe` tools directly, use POSIX paths for configure, and pass `--target` to Clang instead of the old target-prefixed wrapper scripts. The fork retains PATH-case handling in its native command environment. CMake still receives Windows paths with forward slashes, not MSYS drive paths.

For Windows, libvpx and FFmpeg are copied into task-local directories below `app/build/tmp`; configure/make scripts in those copies are normalized to LF without changing other bytes. Source submodules are not rewritten. FFmpeg's version is resolved from the original checkout before building the copy, to avoid accidentally using the parent app's Git revision. Native build outputs and logs remain under `app/build/generated/tgx`. Configure and Make stages append to their build log, so an error does not discard the previous stage's diagnostics.

Generated-file comparison uses `Files.mismatch` instead of memory-mapped buffers. This lets Windows replace generated files immediately, without relying on garbage collection to release file mappings. Unchanged generated files keep their timestamps.

The app's generated-source and native-library directory arguments use forward slashes when passed to CMake, so Windows backslashes are not interpreted as CMake escape sequences.

## Checks

### Verification on 2026-10-06

At product `main` revision `fd94dc5e`, after merging upstream `51a2ba25`:

- JDK 25, Gradle 9.8.0, Windows NDK `30.0.16248370`, CMake 3.22.1 and MSYS2 were used without WSL. Older installed NDKs were retained.
- All 29 build-infrastructure tests and 9 synthetic TDLib resolver fixtures passed. The latter are resolver tests, not a replacement for the real native build.
- `latestArm64Release` rebuilt libvpx, FFmpeg and JNI libraries with r30 and the new pins. The TDLib module supplies r30 prebuilts; its Java API/source version is unchanged.
- 517 modern JVM tests, signed Release packaging, R8/resource shrinking and production-source Release lint passed. Lint found no new issues; 17 existing warnings remain baseline-filtered. Test-source UAST is excluded only by the local validation harness.
- Release identity, Firebase resources, existing v2/v3 certificate, source revision and all 11 packaged native outputs were verified. ZIP/ELF 16 KiB alignment passed; APK and matching R8 mapping were archived together.
- All 56 recursive submodules remain at their exact pins without tracked changes after building. LF/CRLF policy is preserved.

This run did not build new Debug/legacy APKs or repeat the device/server matrix. A subsequent requested installation of the verified signed Release on API 37 passed hash/preservation checks and a cold-start smoke; see the acceptance record for its limits. The first Release attempt stopped at a translation connection timeout after native compilation; retry after restoring VPN access completed successfully. No translation check was bypassed and no old native inputs were substituted.

### Verification on 2026-10-05

At fork revision `4493005b`, after merging upstream `805209e6`:

- JDK 25, Gradle 9.8.0, MSYS2 and the configured Windows SDK were used without WSL.
- All 29 build-infrastructure tests passed, including the real MSYS2 argument check and explicit MSYS2-root precedence.
- `latestArm64Debug` and signed/R8-shrunk `latestArm64Release` passed with NDK 27.3.13750724. `legacyArm32Debug` passed with `-PuseLegacyNdk=true` and NDK 23.2.8568313.
- Native tasks ran against the updated pins; both FFmpeg variants report `e594a51859`. TDLib/OpenSSL prebuilts remain at their pinned upstream revisions, not locally rebuilt substitutes.
- APK identity, service configuration, native inputs and signatures passed validation; modern APKs passed 16 KiB alignment, legacy passed the required v1 signature check.
- All 56 recursive submodules match their pins without tracked edits. Source text uses LF, with only the intentional CRLF checkout of `gradlew.bat`.

These are the three tested configurations, not all-ABI certification. Credentials, signing material, APKs, local harnesses and device captures are not part of this repository.

### Repeatable build checks

```powershell
./gradlew.bat -p buildSrc test
./gradlew.bat :app:patchOpus :app:buildLibvpxLatestArm64
./gradlew.bat :app:assembleLatestArm64Debug
```

The build-host unit tests exercise Windows tool selection, shell argument boundaries, missing-tool diagnostics, LF normalization and the unchanged Unix command/environment path. On Windows, setting `TGX_MSYS2_ROOT` additionally enables a real MSYS2 argument-preservation test; it is skipped when the host is not configured. Passing those tests alone does not establish a successful native library or APK build.
