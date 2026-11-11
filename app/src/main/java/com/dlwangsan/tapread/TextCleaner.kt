package com.dlwangsan.tapread

object TextCleaner {
    private val multiSpace = Regex("\\s+")
    private val codeLike = Regex("""(?i)^(验证码|校验码|code)[:：\s]*\d{4,8}$""")
    private val mostlyDigits = Regex("""^\d{4,8}$""")

    fun clean(raw: String): String {
        return raw
            .replace('\u00A0', ' ')
            .replace(Regex("[\\r\\n]+"), "。")
            .replace(multiSpace, " ")
            .trim()
            .trim('。', '，', ',', '.', ' ', '\t')
    }

    /** Split UTF-16 text without cutting surrogate pairs; prefer sentence boundaries. */
    fun chunks(text: String, limit: Int): List<String> {
        require(limit >= 2)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + limit, text.length)
            if (end < text.length) {
                if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
                val boundary = (end - 1 downTo start + (end - start) / 2)
                    .firstOrNull { text[it] in "。！？.!?；;\n" }
                if (boundary != null) end = boundary + 1
            }
            result += text.substring(start, end)
            start = end
        }
        return result
    }

    fun shouldSkipAutoRead(text: String): Boolean {
        val cleaned = clean(text)
        if (cleaned.length < 2) return true
        if (codeLike.matches(cleaned) || mostlyDigits.matches(cleaned)) return true
        return false
    }
}
