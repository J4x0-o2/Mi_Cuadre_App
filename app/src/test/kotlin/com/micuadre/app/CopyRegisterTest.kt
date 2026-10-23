package com.micuadre.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the user-facing copy against regional voseo; the app speaks neutral Spanish (tú). */
class CopyRegisterTest {

    private val voseoWords = listOf(
        // imperatives
        "tocá", "intentá", "revisá", "ingresá", "permití", "elegí", "escribí", "usá", "terminá",
        "activá", "creá", "agregá", "mirá", "cargá", "esperá", "volvé", "probá", "confirmá",
        "guardá", "borrá", "deslizá", "presioná", "dejá", "pasá", "hacé", "mantené", "buscá",
        "abrí", "anotá", "elegí", "seleccioná", "ponele", "fijate", "asegurate",
        // pronoun imperatives
        "conectate", "desconectate", "registrate", "pausate", "avisame", "decime",
        // present forms
        "tenés", "podés", "querés", "seguís", "sabés", "sos",
        // pronoun
        "vos",
    )

    /** Whole-word, case-insensitive match; letters (accented included) delimit words. */
    private fun containsWord(text: String, word: String): Boolean {
        var from = text.indexOf(word, ignoreCase = true)
        while (from >= 0) {
            val end = from + word.length
            val before = text.getOrNull(from - 1)
            val after = text.getOrNull(end)
            if (before?.isLetter() != true && after?.isLetter() != true) return true
            from = text.indexOf(word, from + 1, ignoreCase = true)
        }
        return false
    }

    private fun stringsFile(): File =
        listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
            .map(::File)
            .firstOrNull { it.isFile }
            ?: error("strings.xml not found from ${File(".").absoluteFile}")

    private fun stringEntries(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val node = nodes.item(it)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    @Test
    fun `strings use neutral spanish without voseo`() {
        val offenders = stringEntries(stringsFile()).flatMap { (key, text) ->
            voseoWords.filter { containsWord(text, it) }
                .map { word -> "$key -> '$word' in \"$text\"" }
        }
        assertTrue("Voseo found in strings.xml:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `guard detects voseo`() {
        assertTrue(containsWord("Tocá Iniciar", "tocá"))
        assertTrue(!containsWord("Toca Iniciar", "tocá"))
    }
}
