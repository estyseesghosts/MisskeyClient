package me.foxtails.palustris

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourceTest {
    private data class Resource(
        val kind: String,
        val value: String,
        val placeholders: Map<String, String>,
        val quantities: Set<String>,
    )

    @Test
    fun localeResourcesMatchTheDefaultCatalog() {
        val resourceRoot = locateResourceRoot()
        val defaultCatalog = parse(resourceRoot.resolve("values/strings.xml"))
        assertTrue("default strings.xml must contain resources", defaultCatalog.isNotEmpty())

        resourceRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .sortedBy { it.name }
            .forEach { localeDirectory ->
                val file = localeDirectory.resolve("strings.xml")
                if (!file.isFile) return@forEach
                val locale = parse(file)
                assertTrue("${localeDirectory.name} contains an unknown key", locale.keys.all { it in defaultCatalog })
                locale.forEach { (key, localized) ->
                    val original = defaultCatalog.getValue(key)
                    assertEquals("$key in ${localeDirectory.name} changed resource kind", original.kind, localized.kind)
                    assertEquals("$key in ${localeDirectory.name} changed placeholders", original.placeholders, localized.placeholders)
                    if (localized.kind == "plurals") {
                        assertEquals("$key in ${localeDirectory.name} changed plural quantities", original.quantities, localized.quantities)
                    }
                }
                val translated = locale.count { (key, value) -> value.value != defaultCatalog.getValue(key).value }
                println("${localeDirectory.name}: translated=$translated englishFallback=${locale.size - translated} keys=${locale.size}")
            }
    }

    @Test
    fun commonPresentationCallsDoNotEmbedUserVisibleEnglishLiterals() {
        val uiRoot = locateUiRoot()
        val exclusions = setOf(
            "URL", "USERNAME", "HASHTAG", "SHA-256", "url.xyz", "Composer", "EditProfile", "NotificationSettings",
        )
        val literalPattern = Regex("""(?:Text|BasicTextField|EmptyState)\s*\(\s*"([^"]+)"""")
        val violations = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                literalPattern.findAll(file.readText()).mapNotNull { match ->
                    val literal = match.groupValues[1]
                    if (literal in exclusions || literal.startsWith("test")) null else "${file.name}: $literal"
                }.toList()
            }
            .toList()
        assertTrue("Extract user-visible literals: $violations", violations.isEmpty())
    }

    private fun parse(file: File): Map<String, Resource> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val resources = document.documentElement
        val result = linkedMapOf<String, Resource>()
        for (index in 0 until resources.childNodes.length) {
            val node = resources.childNodes.item(index)
            if (node !is Element || node.tagName !in setOf("string", "plurals")) continue
            val name = node.getAttribute("name")
            require(name.isNotBlank()) { "${file.name} has an unnamed resource" }
            require(result.put(name, resource(node)) == null) { "${file.name} defines $name more than once" }
        }
        return result
    }

    private fun resource(element: Element): Resource {
        val kind = element.tagName
        val value = if (kind == "plurals") {
            (0 until element.childNodes.length)
                .map { element.childNodes.item(it) }
                .filterIsInstance<Element>()
                .joinToString("|") { it.getAttribute("quantity") + ":" + it.textContent }
        } else {
            element.textContent
        }
        val placeholders = placeholderPattern.findAll(value).associate { it.groupValues[1] to it.groupValues[2] }
        val quantities = if (kind == "plurals") {
            (0 until element.childNodes.length)
                .map { element.childNodes.item(it) }
                .filterIsInstance<Element>()
                .map { it.getAttribute("quantity") }
                .toSet()
                .also { values -> require(values.isNotEmpty() && "other" in values) { "${element.getAttribute("name")} must define other" } }
        } else emptySet()
        return Resource(kind, value, placeholders, quantities)
    }

    private fun locateResourceRoot(): File = sequenceOf(
        File("app/src/main/res"),
        File("src/main/res"),
    ).first { it.isDirectory }

    private fun locateUiRoot(): File = sequenceOf(
        File("app/src/main/java/me/foxtails/palustris/ui"),
        File("src/main/java/me/foxtails/palustris/ui"),
    ).first { it.isDirectory }

    private companion object {
        val placeholderPattern = Regex("%(\\d+)\\$([a-zA-Z])")
    }
}
