package com.dlwangsan.tapread

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context

object ClipboardHelper {
    // Auto-read callers keep the default. Only explicit manual actions may opt in.
    fun readText(context: Context, allowSensitive: Boolean = false): String? =
        runCatching { readTextInternal(context, allowSensitive) }.getOrNull()

    private fun readTextInternal(context: Context, allowSensitive: Boolean): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        val description = clip.description
        if (!allowSensitive && description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return null
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        ) {
            return null
        }
        val text = clip.getItemAt(0).text?.toString()
        return text?.takeIf { it.isNotBlank() }
    }
}
