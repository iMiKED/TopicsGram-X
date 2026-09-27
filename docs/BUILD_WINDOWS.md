# Experimental Windows native build support

This branch adds Windows host support for the libvpx, FFmpeg and Opus preparation tasks. It uses the Windows JDK and Android SDK/NDK, plus MSYS2 for the POSIX configure/make scripts.

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

MSYS2 defaults to `C:/msys64`. For a different installation, set this variable in the PowerShell session that launches Gradle:

```powershell
$env:TGX_MSYS2_ROOT = 'D:/Tools/msys64'
./gradlew.bat :app:assembleLatestArm64Debug
```

This is an ARM64 debug build for the latest SDK flavor, not a verification of every ABI/flavor. A production release additionally requires the project's normal signing and service configuration.

The adapter passes configure scripts directly to MSYS2 Bash without profile scripts or a `bash -c` command string. It invokes MSYS Make/Perl directly and prepends the selected MSYS `usr/bin` to the child process PATH. This preserves arguments containing spaces when launched from Java on Windows. Windows NDK binary tools use their `.exe` filenames. The target-prefixed Clang entry points are shell scripts provided by the NDK and run inside the selected shell.

For Windows, libvpx and FFmpeg are copied into task-local directories below `app/build/tmp`; configure/make scripts in those copies are normalized to LF without changing other bytes. Source submodules are not rewritten. FFmpeg's version is resolved from the original checkout before building the copy, to avoid accidentally using the parent app's Git revision. Native build outputs and logs remain under `app/build/generated/tgx`. Configure and Make stages append to their build log, so an error does not discard the previous stage's diagnostics.

Generated-file comparison uses `Files.mismatch` instead of memory-mapped buffers. This lets Windows replace generated files immediately, without relying on garbage collection to release file mappings. Unchanged generated files keep their timestamps.

The app's generated-source and native-library directory arguments use forward slashes when passed to CMake, so Windows backslashes are not interpreted as CMake escape sequences.

## Checks

```powershell
./gradlew.bat -p buildSrc test
./gradlew.bat :app:patchOpus :app:buildLibvpxLatestArm64
./gradlew.bat :app:assembleLatestArm64Debug
```

The build-host unit tests exercise Windows tool selection, shell argument boundaries, missing-tool diagnostics, LF normalization and the unchanged Unix command/environment path. On Windows, setting `TGX_MSYS2_ROOT` additionally enables a real MSYS2 argument-preservation test; it is skipped when the host is not configured. Passing those tests alone does not establish a successful native library or APK build.
