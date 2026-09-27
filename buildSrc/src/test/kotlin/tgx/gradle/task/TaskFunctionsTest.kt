package tgx.gradle.task

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.attribute.FileTime

class TaskFunctionsTest {
  @get:Rule
  val temporary = TemporaryFolder()

  @Test
  fun fileComparisonHandlesEmptyEqualAndDifferentContent() {
    val a = temporary.newFile("a")
    val b = temporary.newFile("b")
    assertTrue(areFileContentsIdentical(a, b))
    a.writeText("same")
    b.writeText("same")
    assertTrue(areFileContentsIdentical(a, b))
    b.writeText("different")
    assertFalse(areFileContentsIdentical(a, b))
    b.writeText("some")
    assertFalse(areFileContentsIdentical(a, b))
  }

  @Test
  fun comparisonReleasesFilesForImmediateReplacementAndDeletion() {
    val a = temporary.newFile("replace")
    val b = temporary.newFile("delete")
    a.writeBytes(ByteArray(65537) { 1 })
    b.writeBytes(ByteArray(65537) { 2 })
    assertFalse(areFileContentsIdentical(a, b))
    a.writeText("new")
    assertTrue(b.delete())
    assertEquals("new", a.readText())
  }

  @Test
  fun generatedTextCanBeRewrittenWithoutStaleTempFiles() {
    val file = temporary.root.resolve("generated/Output.kt")
    writeToFile(file) { it.append("first\r\n") }
    writeToFile(file) { it.append("second\n") }
    assertEquals("second\n", file.readText())
    assertFalse(file.resolveSibling("${file.name}.temp").exists())
  }

  @Test
  fun identicalGeneratedTextKeepsTimestamp() {
    val file = temporary.newFile("unchanged")
    file.writeText("same")
    Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(1234567890000))
    val modified = file.lastModified()
    writeToFile(file) { it.append("same") }
    assertEquals(modified, file.lastModified())
    assertFalse(file.resolveSibling("${file.name}.temp").exists())
  }
}
