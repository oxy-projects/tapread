import com.dlwangsan.tapread.TextCleaner
fun main() {
    val cases = listOf("", "你好", "甲".repeat(12000), "😀".repeat(6000), "甲".repeat(3998) + "😀乙", "第一句。第二句！第三句？".repeat(700))
    for (text in cases) {
        for (limit in listOf(2, 7, 4000)) {
            val chunks = TextCleaner.chunks(text, limit)
            check(chunks.joinToString("") == text)
            check(chunks.all { it.isNotEmpty() && it.length <= limit })
            check(chunks.none { Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first()) })
        }
    }
    check(!TextCleaner.shouldSkipAutoRead("甲".repeat(12000)))
    check(TextCleaner.shouldSkipAutoRead("验证码：123456"))
    check(TextCleaner.shouldSkipAutoRead("123456"))
    println("PASS: 18 text boundary cases and long-text / verification-code checks")
}
