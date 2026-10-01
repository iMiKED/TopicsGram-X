package tgx.gradle

import org.junit.Assert.*
import org.junit.Test

class AppVariantIdentityTest {
  private val baseId = "com.imiked.topicsgram"

  @Test
  fun debugIsASeparateInstall() {
    assertEquals("com.imiked.topicsgram.debug", baseId + AppVariantIdentity.debugSuffix(false))
  }

  @Test
  fun syntheticChecksRemainSeparateFromBothRealClients() {
    val syntheticId = baseId + AppVariantIdentity.debugSuffix(true)
    assertEquals("com.imiked.topicsgram.debug.stage8synthetic", syntheticId)
    assertTrue(syntheticId.endsWith(".stage8synthetic"))
    assertNotEquals(baseId, syntheticId)
    assertNotEquals(baseId + AppVariantIdentity.debugSuffix(false), syntheticId)
  }

  @Test
  fun releaseIdentityAndNameAreUnchanged() {
    assertEquals("TopicsGram X", AppVariantIdentity.applicationName("TopicsGram X", false))
    assertEquals("com.imiked.topicsgram.sync.account", AppVariantIdentity.accountType(baseId))
    assertEquals("com.imiked.topicsgram.sync.provider", AppVariantIdentity.contentAuthority(baseId))
  }

  @Test
  fun debugNameIsVisibleWithoutRenamingTheProject() {
    assertEquals("TopicsGram X Debug", AppVariantIdentity.applicationName("TopicsGram X", true))
  }

  @Test
  fun resourcesUseFinalApplicationIdIncludingAllSuffixes() {
    for (suffix in listOf("", ".debug", ".debug.stage8synthetic", ".anotherFlavor.debug")) {
      val id = baseId + suffix
      assertEquals("$id.sync.account", AppVariantIdentity.accountType(id))
      assertEquals("$id.sync.provider", AppVariantIdentity.contentAuthority(id))
    }
  }

  @Test
  fun accountAndProviderIdentitiesNeverCollideAcrossVariants() {
    val ids = listOf(baseId, baseId + AppVariantIdentity.debugSuffix(false), baseId + AppVariantIdentity.debugSuffix(true))
    assertEquals(3, ids.map(AppVariantIdentity::accountType).toSet().size)
    assertEquals(3, ids.map(AppVariantIdentity::contentAuthority).toSet().size)
  }
}
