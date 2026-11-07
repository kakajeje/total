package com.kakajeje.meetingnotes.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.zip.ZipInputStream

/** 공유·파일 열기로 들어온 내용을 텍스트로 읽는다. .txt/.md 와 .docx 를 지원. */
object TextImport {

    data class Incoming(val text: String, val fileName: String?)

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    fun read(context: Context, uri: Uri): Incoming? {
        val name = displayName(context, uri)
        val type = context.contentResolver.getType(uri) ?: ""
        val isDocx = name?.endsWith(".docx", true) == true ||
            type == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                if (isDocx) docxToText(ZipInputStream(input)) else input.readBytes().decodeText()
            }
        }.getOrNull() ?: return null
        return Incoming(text.trim(), name)
    }

    private fun ByteArray.decodeText(): String {
        // UTF-8 BOM 제거, 깨지면 EUC-KR(MS949)로 다시 시도
        val utf8 = toString(Charsets.UTF_8).removePrefix("﻿")
        return if (utf8.contains('�')) runCatching { toString(charset("MS949")) }.getOrDefault(utf8) else utf8
    }

    private fun docxToText(zip: ZipInputStream): String {
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val xml = zip.readBytes().toString(Charsets.UTF_8)
                return xml
                    .replace(Regex("</w:p>"), "\n")
                    .replace(Regex("<w:tab/>"), "\t")
                    .replace(Regex("<w:br[^>]*/>"), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                    .replace("&apos;", "'").replace("&amp;", "&")
            }
            entry = zip.nextEntry
        }
        return ""
    }
}
