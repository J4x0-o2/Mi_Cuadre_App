package com.micuadre.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Guards the backup policy: the Room database and the DataStore settings are backed up and transferred. */
class BackupRulesTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun mainFile(relative: String): File =
        listOf("src/main/$relative", "app/src/main/$relative")
            .map(::File)
            .firstOrNull { it.isFile }
            ?: error("$relative not found from ${File(".").absoluteFile}")

    private fun parse(relative: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(mainFile(relative)).documentElement

    private fun Element.child(tag: String): Element =
        getElementsByTagName(tag).item(0) as? Element ?: error("<$tag> missing in <${this.tagName}>")

    /** domain:path pairs of every <include> directly under this element. */
    private fun Element.includes(): Set<String> =
        (0 until getElementsByTagName("include").length)
            .map { getElementsByTagName("include").item(it) as Element }
            .map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }
            .toSet()

    private val expected = setOf("database:.", "file:datastore/")

    private fun application(): Element = parse("AndroidManifest.xml").child("application")

    @Test
    fun `manifest enables backup and points to both rule files`() {
        val app = application()
        assertEquals("true", app.getAttributeNS(androidNs, "allowBackup"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(androidNs, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(androidNs, "dataExtractionRules"))
    }

    @Test
    fun `cloud backup includes database and datastore`() {
        val rules = parse("res/xml/data_extraction_rules.xml")
        assertEquals(expected, rules.child("cloud-backup").includes())
    }

    @Test
    fun `device transfer includes database and datastore`() {
        val rules = parse("res/xml/data_extraction_rules.xml")
        assertEquals(expected, rules.child("device-transfer").includes())
    }

    @Test
    fun `full backup content includes database and datastore`() {
        val rules = parse("res/xml/backup_rules.xml")
        assertEquals(expected, rules.includes())
        assertTrue(rules.tagName == "full-backup-content")
    }
}
