package tgx.gradle

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeBuildHostTest {
  @get:Rule
  val temporary = TemporaryFolder()

  private fun msysHost(extraEnvironment: Map<String, String> = emptyMap()): NativeBuildHost {
    val root = temporary.newFolder("MSYS root's directory")
    val bin = root.resolve("usr/bin")
    check(bin.mkdirs())
    for (name in listOf("bash", "make", "perl")) {
      bin.resolve("$name.exe").writeText("")
    }
    return NativeBuildHost(true, mapOf("TGX_MSYS2_ROOT" to root.absolutePath) + extraEnvironment)
  }

  @Test
  fun unixCommandsAreUnchanged() {
    val arguments = listOf("make", "-j2", "install")
    assertEquals(arguments, NativeBuildHost(false, emptyMap()).commandLine(arguments))
  }

  @Test
  fun windowsNdkToolsUseExeSuffix() {
    val prebuilt = temporary.newFolder("ndk")
    val bin = prebuilt.resolve("bin")
    check(bin.mkdir())
    val unixTool = bin.resolve("llvm-ar").apply { writeText("") }
    val windowsTool = bin.resolve("llvm-ar.exe").apply { writeText("") }
    assertEquals(windowsTool, NativeBuildHost(true).ndkTool(prebuilt, "llvm-ar"))
    assertEquals(unixTool, NativeBuildHost(false).ndkTool(prebuilt, "llvm-ar"))
  }

  @Test
  fun missingWindowsToolDoesNotFallBackToUnixFile() {
    val prebuilt = temporary.newFolder("incomplete-ndk")
    check(prebuilt.resolve("bin").mkdir())
    prebuilt.resolve("bin/llvm-ar").writeText("")
    assertThrows(RuntimeException::class.java) {
      NativeBuildHost(true).ndkTool(prebuilt, "llvm-ar")
    }
  }

  @Test
  fun shellArgumentsAreNotInterpolated() {
    val arguments = listOf("D:/project with spaces/configure", "--prefix=a'b; printf unsafe", "\$value")
    val command = msysHost().commandLine(arguments)
    assertTrue(command.first().endsWith("/usr/bin/bash.exe"))
    assertEquals(listOf("--noprofile", "--norc"), command.subList(1, 3))
    assertEquals(arguments, command.takeLast(arguments.size))
  }

  @Test
  fun windowsShellPreservesArgumentsAtRuntime() {
    assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
    assumeTrue(!System.getenv("TGX_MSYS2_ROOT").isNullOrBlank())
    val script = temporary.newFile("echo arguments.sh")
    script.writeText("#!/bin/sh\ncommand -v diff >/dev/null || exit 5\ncommand -v sed >/dev/null || exit 6\nprintf '<%s>\\n' \"\$@\"\n")
    val arguments = listOf("--extra-cflags=-O2 -fPIC -fpie", "literal ' quote; \$value")
    val host = NativeBuildHost()
    val builder = ProcessBuilder(host.commandLine(listOf(script.nativePath()) + arguments))
      .redirectErrorStream(true)
    builder.environment().putAll(host.commandEnvironment())
    val process = builder.start()
    val output = process.inputStream.bufferedReader().readText()
    assertEquals(output, 0, process.waitFor())
    assertEquals(arguments.joinToString("") { "<$it>\n" }, output.replace("\r\n", "\n"))
  }

  @Test
  fun makeAndPerlComeFromTheSelectedMsysRoot() {
    val host = msysHost()
    for (name in listOf("make", "perl")) {
      val command = host.commandLine(listOf(name, "argument with spaces"))
      assertTrue(command[0].endsWith("/usr/bin/$name.exe"))
      assertEquals("argument with spaces", command[1])
    }
  }

  @Test
  fun windowsEnvironmentPrependsMsysWithoutChangingFlags() {
    val flags = "-O2 -fPIC -I/path with spaces"
    val environment = msysHost().commandEnvironment(mapOf("PATH" to "ndk-tools", "CFLAGS" to flags))
    assertTrue(environment["PATH"]!!.endsWith("/usr/bin${java.io.File.pathSeparator}ndk-tools"))
    assertEquals(flags, environment["CFLAGS"])
  }

  @Test
  fun unixEnvironmentIsUnchanged() {
    val environment = mapOf("PATH" to "/bin", "CFLAGS" to "-O2 -fPIC")
    assertEquals(environment, NativeBuildHost(false).commandEnvironment(environment))
  }

  @Test
  fun windowsEnvironmentReusesInheritedPathCasing() {
    val host = msysHost(mapOf("Path" to "inherited"))
    val environment = host.commandEnvironment(mapOf("PATH" to "ndk-tools"))
    assertFalse(environment.containsKey("PATH"))
    assertTrue(environment["Path"]!!.endsWith("/usr/bin${java.io.File.pathSeparator}ndk-tools"))
  }

  @Test
  fun missingMsysHasActionableError() {
    val root = temporary.root.resolve("missing")
    val host = NativeBuildHost(true, mapOf("TGX_MSYS2_ROOT" to root.absolutePath))
    val exception = assertThrows(IllegalArgumentException::class.java) {
      host.commandLine(listOf("make"))
    }
    assertTrue(exception.message!!.contains("TGX_MSYS2_ROOT"))
  }

  @Test
  fun nativePathsUseForwardSlashes() {
    assertFalse(temporary.newFile("path with spaces").nativePath().contains('\\'))
  }

  @Test
  fun generatedScriptsUseLfWithoutRewritingBinaryData() {
    for (name in listOf("configure", "Makefile", "rules.mk", "common.mak", "helper.sh", "helper.pl")) {
      val file = temporary.newFile(name)
      file.writeText("first\r\nsecond\r\n")
      normalizeNativeScript(file)
      assertEquals("first\nsecond\n", file.readText())
    }
    val binary = temporary.newFile("data.bin")
    val bytes = byteArrayOf(0, 13, 10, -1)
    binary.writeBytes(bytes)
    normalizeNativeScript(binary)
    assertArrayEquals(bytes, binary.readBytes())
  }

  @Test
  fun armAssemblyCopyUsesLfBeforeAds2gasConversion() {
    val source = temporary.newFolder("checkout").resolve("idct_neon.asm")
    val original = "    INCLUDE ./vpx_config.asm\r\n    AREA text, CODE\r\n".toByteArray()
    source.writeBytes(original)
    val generated = temporary.newFolder("prepared").resolve(source.name)
    source.copyTo(generated)
    normalizeNativeScript(generated)
    assertEquals("    INCLUDE ./vpx_config.asm\n    AREA text, CODE\n", generated.readText())
    assertArrayEquals(original, source.readBytes())
    normalizeNativeScript(generated)
    assertFalse(generated.readText().contains('\r'))
  }

  @Test
  fun assemblyNormalizationIsCaseInsensitiveAndPreservesLoneCr() {
    val file = temporary.newFile("legacy.ASM")
    file.writeBytes(byteArrayOf(-1, 13, 10, 13, 42))
    normalizeNativeScript(file)
    assertArrayEquals(byteArrayOf(-1, 10, 13, 42), file.readBytes())
  }

  @Test
  fun scriptNormalizationPreservesNonUtf8BytesAndLoneCr() {
    val file = temporary.newFile("legacy-encoding.pl")
    file.writeBytes(byteArrayOf(-1, 13, 10, 13, 42))
    normalizeNativeScript(file)
    assertArrayEquals(byteArrayOf(-1, 10, 13, 42), file.readBytes())
  }
}
