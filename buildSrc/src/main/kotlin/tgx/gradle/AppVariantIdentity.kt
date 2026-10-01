package tgx.gradle

/** Identities derived from AGP's final application ID, not the shared defaultConfig ID. */
object AppVariantIdentity {
  fun debugSuffix(synthetic: Boolean): String =
    if (synthetic) ".debug.stage8synthetic" else ".debug"

  fun applicationName(baseName: String, debug: Boolean): String =
    if (debug) "$baseName Debug" else baseName

  fun accountType(applicationId: String): String = "$applicationId.sync.account"

  fun contentAuthority(applicationId: String): String = "$applicationId.sync.provider"
}
