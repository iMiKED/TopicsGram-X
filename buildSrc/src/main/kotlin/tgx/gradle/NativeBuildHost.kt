package tgx.gradle

import java.io.File
import java.util.Locale

/** Host tools used by the configure/make dependencies, not by Android CMake. */
class NativeBuildHost(
  val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
  private val environment: Map<String, String> = System.getenv()
) {
  private fun msysBin(): File {
    val root = environment["TGX_MSYS2_ROOT"]?.takeIf { it.isNotBlank() } ?: "C:/msys64"
    return File(root).resolve("usr/bin")
  }

  private fun msysTool(name: String): String {
    val executable = msysBin().resolve("$name.exe")
    require(executable.isFile) {
      "MSYS2 tool not found: ${executable.absolutePath}. " +
        "Install MSYS2 with make, perl and diffutils, then set TGX_MSYS2_ROOT to its root directory."
    }
    return executable.nativePath()
  }

  fun ndkTool(prebuilt: File, name: String): File = requireFile(
    prebuilt.resolve("bin/$name${if (isWindows) ".exe" else ""}")
  )

  fun commandLine(arguments: List<String>): List<String> {
    require(arguments.isNotEmpty())
    if (!isWindows) return arguments

    // No bash -c wrapper: Java/Windows quoting must not turn a quoted "$@" into $@.
    // Pass scripts directly to Bash and invoke MSYS executables directly.
    return when (arguments.first()) {
      "make", "perl" -> listOf(msysTool(arguments.first())) + arguments.drop(1)
      else -> listOf(msysTool("bash"), "--noprofile", "--norc") + arguments
    }
  }

  fun commandEnvironment(overrides: Map<String, String> = emptyMap()): Map<String, String> {
    if (!isWindows) return overrides
    // Windows names are case-insensitive, but the Java/Gradle environment maps
    // can contain both Path and PATH. Replace the inherited key, don't add a twin.
    val pathKey = environment.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
    val overriddenPath = overrides.entries.lastOrNull { it.key.equals("PATH", ignoreCase = true) }?.value
    val path = listOfNotNull(msysBin().nativePath(), overriddenPath ?: environment[pathKey])
      .joinToString(File.pathSeparator)
    return overrides.filterKeys { !it.equals("PATH", ignoreCase = true) } + (pathKey to path)
  }
}

/** Forward slashes work with Windows NDK tools as well as MSYS2 configure scripts. */
fun File.nativePath(): String = absolutePath.replace('\\', '/')

/** Applied only to generated copies: never rewrite a third-party checkout. */
fun normalizeNativeScript(file: File) {
  if (file.name == "configure" || file.name.startsWith("Makefile") ||
    file.extension.lowercase(Locale.ROOT) in setOf("sh", "pl", "mk", "mak")) {
    val original = file.readBytes()
    val normalized = ByteArray(original.size)
    var length = 0
    for (index in original.indices) {
      if (original[index] == 13.toByte() && original.getOrNull(index + 1) == 10.toByte()) continue
      normalized[length++] = original[index]
    }
    if (length != original.size) file.writeBytes(normalized.copyOf(length))
  }
}
