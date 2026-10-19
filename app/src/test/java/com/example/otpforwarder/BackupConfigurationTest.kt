package com.example.otpforwarder

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/** Verify both backup formats preserve configuration without including unrelated files. */
class BackupConfigurationTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private val expected = setOf(
        "database" to "otp_forwarder_database",
        "database" to "otp_forwarder_database-wal",
        "database" to "otp_forwarder_database-shm",
        "sharedpref" to "forwarder_prefs.xml"
    )

    private fun read(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File("src/main/$path")).documentElement

    private fun includes(element: Element): Set<Pair<String, String>> {
        val nodes = element.getElementsByTagName("include")
        return (0 until nodes.length).map {
            val include = nodes.item(it) as Element
            include.getAttribute("domain") to include.getAttribute("path")
        }.toSet()
    }

    @Test fun manifestEnablesBackupAndKeepDataOption() {
        val app = read("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        assertEquals("true", app.getAttributeNS(androidNamespace, "allowBackup"))
        assertEquals("true", app.getAttributeNS(androidNamespace, "hasFragileUserData"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(androidNamespace, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(androidNamespace, "dataExtractionRules"))
    }

    @Test fun legacyBackupIncludesOnlyRulesAndPreferences() {
        assertEquals(expected, includes(read("res/xml/backup_rules.xml")))
    }

    @Test fun modernCloudAndDeviceTransferUseTheSameAllowlist() {
        val root = read("res/xml/data_extraction_rules.xml")
        for (mode in listOf("cloud-backup", "device-transfer")) {
            val scopes = root.getElementsByTagName(mode)
            assertEquals(1, scopes.length)
            assertEquals(expected, includes(scopes.item(0) as Element))
        }
    }
}
