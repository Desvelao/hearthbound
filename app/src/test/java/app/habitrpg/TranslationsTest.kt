package app.habitrpg

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every string must exist in every language with the same placeholders, and every plural with the quantities the
 * language needs. Android lint's MissingTranslation covers the first part too; this also checks the placeholders and the
 * plurals, which lint does not compare. Reads the real resource files, so it needs no emulator.
 */
class TranslationsTest {
    private val languages = listOf("es", "de", "fr", "it")
    private val modules = listOf("app", "core/data", "feature/tasks", "feature/character", "feature/rewards", "feature/settings")

    private fun resDir(module: String): File {
        // Gradle runs unit tests from the module directory.
        val up = File("..").canonicalFile
        return File(up, "$module/src/main/res").takeIf { it.isDirectory } ?: File("../$module/src/main/res")
    }

    private data class Entry(val text: String)

    private fun load(file: File): Pair<Map<String, String>, Map<String, Map<String, String>>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i) as? Element ?: continue
            when (n.tagName) {
                "string" -> strings[n.getAttribute("name")] = n.textContent
                "plurals" -> {
                    val items = n.getElementsByTagName("item")
                    plurals[n.getAttribute("name")] = (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to item.textContent
                    }
                }
            }
        }
        return strings to plurals
    }

    private fun placeholders(text: String) = Regex("%\\d+\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    @Test fun everyModuleHasTheSameStringsInEveryLanguage() {
        modules.forEach { module ->
            val base = File(resDir(module), "values/strings.xml")
            assertTrue("missing ${base.path}", base.exists())
            val (en, enPlurals) = load(base)
            assertTrue("$module has no strings", en.isNotEmpty())
            languages.forEach { lang ->
                val file = File(resDir(module), "values-$lang/strings.xml")
                assertTrue("missing ${file.path}", file.exists())
                val (strings, plurals) = load(file)
                assertEquals("$module [$lang] string keys", en.keys.sorted(), strings.keys.sorted())
                assertEquals("$module [$lang] plural keys", enPlurals.keys.sorted(), plurals.keys.sorted())
                en.forEach { (key, text) ->
                    assertEquals("$module [$lang] $key placeholders", placeholders(text), placeholders(strings.getValue(key)))
                    assertTrue("$module [$lang] $key is empty", strings.getValue(key).isNotBlank())
                }
                enPlurals.forEach { (key, items) ->
                    val translated = plurals.getValue(key)
                    assertTrue("$module [$lang] $key needs one and other", "one" in translated && "other" in translated)
                    translated.forEach { (quantity, text) ->
                        assertEquals("$module [$lang] $key[$quantity] placeholders", placeholders(items.getValue("other")), placeholders(text))
                    }
                }
            }
        }
    }

    @Test fun noTranslationIsJustTheEnglishTextCopied() {
        // A few words are legitimately the same in several languages (OK, XP, Mana), so only long texts are checked.
        modules.forEach { module ->
            val (en, _) = load(File(resDir(module), "values/strings.xml"))
            languages.forEach { lang ->
                val (strings, _) = load(File(resDir(module), "values-$lang/strings.xml"))
                en.filter { it.value.length > 40 }.forEach { (key, text) ->
                    assertTrue("$module [$lang] $key was not translated", strings.getValue(key) != text)
                }
            }
        }
    }
}
