package com.willian.nuvem.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * Leitura simples de .docx, .xlsx e .pptx (que são pacotes ZIP com XML): extrai o texto,
 * sem formatação. Arquivos antigos (.doc, .xls, .ppt) precisam de outro aplicativo.
 */
object OfficeText {
    private fun name(p: XmlPullParser) = p.name.substringAfter(':')

    fun docx(f: File): String = ZipFile(f).use { z ->
        val e = z.getEntry("word/document.xml") ?: return@use ""
        z.getInputStream(e).use { ins ->
            val p = Xml.newPullParser().apply { setInput(ins, "UTF-8") }
            val sb = StringBuilder()
            var inT = false
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (name(p)) {
                        "t" -> inT = true
                        "tab" -> sb.append('\t')
                        "br" -> sb.append('\n')
                    }
                    XmlPullParser.END_TAG -> when (name(p)) {
                        "t" -> inT = false
                        "p" -> sb.append('\n')
                    }
                    XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                }
                ev = p.next()
            }
            sb.toString().trim()
        }
    }

    fun pptx(f: File): String = ZipFile(f).use { z ->
        val slides = z.entries().toList()
            .filter { it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
            .sortedBy { it.name.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val sb = StringBuilder()
        slides.forEachIndexed { i, e ->
            sb.append("— Slide ${i + 1} —\n")
            z.getInputStream(e).use { ins ->
                val p = Xml.newPullParser().apply { setInput(ins, "UTF-8") }
                var inT = false
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    when (ev) {
                        XmlPullParser.START_TAG -> if (name(p) == "t") inT = true
                        XmlPullParser.END_TAG -> when (name(p)) {
                            "t" -> inT = false
                            "p" -> sb.append('\n')
                        }
                        XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                    }
                    ev = p.next()
                }
            }
            sb.append('\n')
        }
        sb.toString().trim()
    }

    /** Primeira planilha como lista de linhas (até 2000 linhas e 60 colunas). */
    fun xlsx(f: File): List<List<String>> = ZipFile(f).use { z ->
        val shared = ArrayList<String>()
        z.getEntry("xl/sharedStrings.xml")?.let { e ->
            z.getInputStream(e).use { ins ->
                val p = Xml.newPullParser().apply { setInput(ins, "UTF-8") }
                var cur: StringBuilder? = null
                var inT = false
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    when (ev) {
                        XmlPullParser.START_TAG -> when (name(p)) {
                            "si" -> cur = StringBuilder()
                            "t" -> inT = true
                        }
                        XmlPullParser.END_TAG -> when (name(p)) {
                            "t" -> inT = false
                            "si" -> { shared.add(cur?.toString().orEmpty()); cur = null }
                        }
                        XmlPullParser.TEXT -> if (inT) cur?.append(p.text)
                    }
                    ev = p.next()
                }
            }
        }
        val sheet = z.getEntry("xl/worksheets/sheet1.xml")
            ?: z.entries().toList().filter { it.name.startsWith("xl/worksheets/sheet") }.minByOrNull { it.name }
            ?: return@use emptyList()
        val rows = ArrayList<List<String>>()
        z.getInputStream(sheet).use { ins ->
            val p = Xml.newPullParser().apply { setInput(ins, "UTF-8") }
            var row = ArrayList<String>()
            var col = 0
            var type = ""
            var inV = false
            var inT = false
            var value = StringBuilder()
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT && rows.size < 2000) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (name(p)) {
                        "row" -> row = ArrayList()
                        "c" -> {
                            type = p.getAttributeValue(null, "t") ?: ""
                            col = columnIndex(p.getAttributeValue(null, "r") ?: "")
                            value = StringBuilder()
                        }
                        "v" -> inV = true
                        "t" -> inT = true
                    }
                    XmlPullParser.END_TAG -> when (name(p)) {
                        "v" -> inV = false
                        "t" -> inT = false
                        "c" -> {
                            if (col < 60) {
                                while (row.size < col) row.add("")
                                val text = if (type == "s") shared.getOrElse(value.toString().trim().toIntOrNull() ?: -1) { "" } else value.toString()
                                row.add(text)
                            }
                        }
                        "row" -> rows.add(row)
                    }
                    XmlPullParser.TEXT -> if (inV || inT) value.append(p.text)
                }
                ev = p.next()
            }
        }
        rows
    }

    /** "C5" -> 2 */
    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break
        }
        return maxOf(0, n - 1)
    }
}
