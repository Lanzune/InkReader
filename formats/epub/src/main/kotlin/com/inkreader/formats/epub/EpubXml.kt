package com.inkreader.formats.epub

import com.inkreader.kernel.api.BookReadException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

internal object EpubXml {
    fun parse(bytes: ByteArray): Document {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            runCatching { factory.isExpandEntityReferences = false }
            runCatching {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
            runCatching {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            runCatching {
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            runCatching {
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            runCatching { factory.isXIncludeAware = false }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            return builder.parse(ByteArrayInputStream(bytes))
        } catch (failure: Exception) {
            throw BookReadException("EPUB XML 解析失败：${failure.message ?: failure.javaClass.simpleName}", failure)
        }
    }

    fun elements(parent: Element, localName: String): List<Element> {
        val nodes = parent.getElementsByTagNameNS("*", localName)
        return buildList {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                if (node is Element) add(node)
            }
        }
    }

    fun first(parent: Element?, localName: String): Element? =
        parent?.let { elements(it, localName).firstOrNull() }

    fun attribute(element: Element, name: String): String? =
        element.getAttribute(name).takeIf { it.isNotBlank() }

    fun text(element: Element?): String {
        if (element == null) return ""
        val builder = StringBuilder()
        appendText(element, builder)
        return builder.toString().trim()
    }

    private fun appendText(node: Node, builder: StringBuilder) {
        when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> builder.append(node.nodeValue)
            Node.ELEMENT_NODE -> {
                var child = node.firstChild
                while (child != null) {
                    appendText(child, builder)
                    child = child.nextSibling
                }
            }
        }
    }
}
