package com.duel2048.shared.social

import kotlin.random.Random

data class MathProblem(val id: String, val prompt: String, val answer: String, val choices: List<String>)
data class WordEntry(val word: String, val clue: String)
data class WordProblem(val id: String, val prompt: String, val grid: List<String>, val answer: String, val seconds: Int)

/** Loads a specific resource catalog; the full match catalog exists only in the server module. */
class PuzzleCatalog(private val resourceRoot: String) {
    private val mathData by lazy { Puzzles.mathLevels.associateWith { level ->
        rows("math/$level.csv", listOf("id", "prompt", "answer", "option_1", "option_2", "option_3", "option_4")).map { row ->
            val options = row.drop(3)
            require(options.size == 4 && options.distinct().size == 4 && row[2] in options) { "Invalid choices in $level: ${row[0]}" }
            options.forEach { it.toBigInteger() }
            MathRules.validate(level, MathProblem(row[0], row[1], row[2], options))
        }.also { require(it.map { p -> p.id }.distinct().size == it.size) { "Duplicate problem id in $level" } }
    } }
    private val wordData by lazy { Puzzles.wordLengths.associateWith { length ->
        rows("words/$length.csv", listOf("word", "clue")).map { row ->
            require(row[0].matches(Regex("[A-Z]{$length}")) && row[1].isNotBlank()) { "Invalid $length-letter entry" }
            WordEntry(row[0], row[1])
        }.also { require(it.map { p -> p.word }.distinct().size == it.size) { "Duplicate dictionary word" } }
    } }

    fun mathEntries(level: String): List<MathProblem> = mathData.getValue(level)
    fun wordEntries(length: Int): List<WordEntry> = wordData.getValue(length)
    fun math(level: String, random: Random = Random.Default): MathProblem =
        mathEntries(level).random(random).let { it.copy(choices = it.choices.shuffled(random)) }

    fun word(length: Int, random: Random = Random.Default): WordProblem {
        val entries = wordEntries(length)
        val index = random.nextInt(entries.size)
        val entry = entries[index]
        var letters = entry.word.toList().shuffled(random).joinToString("")
        if (letters == entry.word) letters = letters.drop(1) + letters.first()
        return WordProblem("${resourceRoot.substringAfterLast('/')}-word-$length-${index + 1}", entry.clue, listOf(letters), entry.word, 20 + length * 5)
    }

    private fun rows(path: String, header: List<String>): List<List<String>> {
        val stream = checkNotNull(PuzzleCatalog::class.java.getResourceAsStream("$resourceRoot/$path")) { "Missing puzzle dataset: $path" }
        val lines = stream.bufferedReader(Charsets.UTF_8).use { it.readLines() }.filter { it.isNotBlank() }
        require(lines.isNotEmpty() && Puzzles.csv(lines.first().removePrefix("\uFEFF")) == header) { "Invalid CSV header: $path" }
        return lines.drop(1).map { Puzzles.csv(it).also { row -> require(row.size == header.size) { "Invalid row in $path" } } }
            .also { require(it.isNotEmpty()) { "Empty dataset: $path" } }
    }

}

/** Small offline training catalog and shared input validation. Never reads match datasets. */
object Puzzles {
    val mathLevels = listOf("easy", "medium", "hard", "very_hard")
    val wordLengths = (3..8).toList()
    private val training = PuzzleCatalog("/datasets/training")
    private val gridDictionary by lazy { WordGrid.dictionary(training) }
    fun wordGrid(size: Int, random: Random = Random.Default) = WordGrid.generate(size, gridDictionary, random)
    fun isGridWord(word: String) = word in gridDictionary
    fun gridSolutions(grid: List<String>) = gridDictionary.filter { WordGrid.findPath(grid, it) != null }.sorted()
    fun mathEntries(level: String) = training.mathEntries(level)
    fun wordEntries(length: Int) = training.wordEntries(length)
    fun math(level: String, random: Random = Random.Default) = training.math(level, random)
    fun word(length: Int, random: Random = Random.Default) = training.word(length, random)

    /** Legacy local saves can obtain choices without replacing their original question. */
    fun choicesFor(answer: String, random: Random = Random.Default): List<String> {
        val value = answer.toBigInteger()
        return listOf(value, value + 1.toBigInteger(), value - 1.toBigInteger(), value + 2.toBigInteger())
            .map { it.toString() }.shuffled(random)
    }

    fun selectedWord(grid: List<String>, indices: List<Int>): String? {
        val letters = grid.joinToString("")
        if (indices.isEmpty() || indices.size > 16 || indices.distinct().size != indices.size || indices.any { it !in letters.indices }) return null
        return indices.map { letters[it] }.joinToString("")
    }

    /** Single-line CSV records, including quoted commas and escaped double quotes. */
    internal fun csv(line: String): List<String> {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { fields.add(field.toString().trim()); field.setLength(0) }
                else -> field.append(c)
            }
            i++
        }
        require(!quoted) { "Unclosed CSV quote" }
        fields.add(field.toString().trim())
        return fields
    }
}
