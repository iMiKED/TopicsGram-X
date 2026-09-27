# TDLib prebuilt selection

The pinned TDLib wrapper `8235f59dacdecbfc61f303d41654123cdbac2d44` contains Java bindings for revision `42e6a5259551178d1dab54a22ad96d14bd906e20`, but the ARM64 library in `src/main/libs/27.3.13750724/arm64-v8a/` embeds revision `d1085f9cebc5a62379991ae1652673954f229c1f`. The compatible binary is already present in the same bundle, under `src/main/libs/27.3.13750724/libs/arm64-v8a/`.

The old fixed CMake path packaged the stale binary. Compilation and signing succeeded, but `JNI_OnLoad` aborted at startup with `Mismatched TdApi.java (...) and tdjni shared library (...) versions`.

`app/jni/cmake/ResolveTdlib.cmake` now:

1. Validates `version.txt` and the Java `GIT_COMMIT_HASH` declaration.
2. Checks the standard and nested prebuilt locations for the selected NDK/ABI.
3. Selects a binary containing the exact standalone revision string embedded by the TDLib JNI generator, preferring the standard layout when both match.
4. Fails configuration when neither matches. TDLib's runtime version check remains unchanged.

The source submodule, Java hash and binaries are not patched. Selected version inputs are registered as CMake configure dependencies. This check prevents the observed revision mismatch; it is not a replacement for testing ABI compatibility or runtime behavior on a device.

## Regression tests

From the repository root, using the Android SDK's CMake (adjust the SDK path as needed):

```powershell
$testRoot = Join-Path (Get-Location) ('app/build/tmp/tdlib-selection-' + [guid]::NewGuid().ToString('N'))
& 'C:/Android/SDK/cmake/3.22.1/bin/cmake.exe' "-DTEST_ROOT=$testRoot" -P app/jni/cmake/tests/ResolveTdlibTest.cmake
```

All 9 scenarios passed, also with spaces in the temporary path: standard layout, nested fallback, nested-only layout, standard preference, stale binaries, missing binaries, mismatched Java bindings, non-exact hash prefix, and invalid version metadata.

The packaged ARM64 `libtdjni.so` SHA-256 was verified as `857FDF42A5919AC9687A11AC0FD056C476A08F7CD409701DFBF36D36D1FD3FBA`, matching the selected source binary. The rebuilt APK launched to the welcome screen on Pixel 6 / Android 17. Other ABIs/flavors have not been built or tested.
