package tgx.gradle

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GoogleServicesTest {
  @get:Rule val temporary = TemporaryFolder()

  private fun fixture(): File = temporary.newFile("google-services.json").apply {
    writeText("""{
      "project_info": {
        "project_number": "123", "project_id": "fixture-project",
        "firebase_url": "https://example.invalid", "storage_bucket": "fixture-bucket"
      },
      "client": [
        {"client_info": {"mobilesdk_app_id": "release-app",
          "android_client_info": {"package_name": "com.example.product"}},
         "api_key": [{"current_key": "release-fixture"}]},
        {"client_info": {"mobilesdk_app_id": "debug-app",
          "android_client_info": {"package_name": "com.example.product.debug"}},
         "api_key": [{"current_key": "debug-fixture"}]}
      ]
    }""")
  }

  @Test fun selectsTheExactReleaseClient() {
    val file = fixture()
    assertTrue(hasGoogleServicesClient(file, "com.example.product"))
    val values = googleServicesValues(file, "com.example.product")
    assertEquals("release-app", values["google_app_id"])
    assertEquals("release-fixture", values["google_api_key"])
  }

  @Test fun debugNeverReusesTheReleaseClient() {
    val file = fixture()
    assertTrue(hasGoogleServicesClient(file, "com.example.product.debug"))
    val values = googleServicesValues(file, "com.example.product.debug")
    assertEquals("debug-app", values["google_app_id"])
    assertEquals("debug-fixture", values["google_crash_reporting_api_key"])
  }

  @Test fun missingAndSyntheticClientsAreNotEnabled() {
    val file = fixture()
    assertFalse(hasGoogleServicesClient(file, "com.example.product.debug.stage8synthetic"))
    assertFalse(hasGoogleServicesClient(file, "com.example.product.other"))
    assertFalse(hasGoogleServicesClient(File(temporary.root, "absent.json"), "com.example.product"))
  }

  @Test fun preservesRequiredAndOptionalFirebaseResources() {
    val values = googleServicesValues(fixture(), "com.example.product.debug")
    assertEquals("123", values["gcm_defaultSenderId"])
    assertEquals("fixture-project", values["project_id"])
    assertEquals("https://example.invalid", values["firebase_database_url"])
    assertEquals("fixture-bucket", values["google_storage_bucket"])
    assertEquals(7, values.size)
  }

  @Test fun refusesToGenerateValuesForAnUnknownClient() {
    assertThrows(org.gradle.api.GradleException::class.java) {
      googleServicesValues(fixture(), "com.example.unknown")
    }
  }

  // Wiring contracts supplement actual helper tests; APK resource checks cover generated output.
  private fun buildScript(): String {
    val start = File(System.getProperty("user.dir")).absoluteFile
    val root = generateSequence(start) { it.parentFile }
      .first { File(it, "app/build.gradle.kts").isFile }
    return File(root, "app/build.gradle.kts").readText().replace(Regex("\\s+"), "")
  }

  @Test fun messagingAndResourcesUseTheFinalVariantId() {
    val source = buildScript()
    assertTrue(source.contains("hasGoogleServicesClient(usedGoogleServices,variant.applicationId.get())"))
    assertTrue(source.contains("googleServicesValues(customGoogleServices,variant.applicationId.get())"))
    assertTrue(source.contains("valfirebaseEnabled=!stage8Synthetic&&"))
    assertTrue(source.contains("checkNotNull(variant.buildConfigFields).put(\"FIREBASE_MESSAGING_ENABLED\",BuildConfigField(\"boolean\",firebaseEnabled,"))
  }

  @Test fun validationUsesTheSameExternalFileAndFinalId() {
    val source = buildScript()
    assertTrue(source.contains("applicationId.set(variant.applicationId)"))
    assertTrue(source.contains("googleServicesJson.fileValue(usedGoogleServices)"))
    assertTrue(source.contains("if(customGoogleServices==null){apply(plugin=libs.plugins.google.services.get().pluginId)}"))
  }
}
