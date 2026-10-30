package com.duel2048.shared.social

/** Rules applied to packaged training questions and published server questions. */
object MathRules {
    fun validate(level: String, problem: MathProblem): MathProblem {
        require(level in Puzzles.mathLevels) { "Unknown math level" }
        val parts = Regex("(\\d{1,30})\\s+([+*/-])\\s+(\\d{1,30})").matchEntire(problem.prompt)?.groupValues
            ?: throw IllegalArgumentException("Use nonnegative integer operands with one operation")
        val a = parts[1].toBigInteger()
        val b = parts[3].toBigInteger()
        val zero = java.math.BigInteger.ZERO
        val result = when (parts[2]) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            else -> {
                require(b > zero && a.remainder(b) == zero) { "Division requires a nonzero divisor and integer answer" }
                a / b
            }
        }
        require(result >= zero && result.toString() == problem.answer) { "Answer must match arithmetic and be nonnegative" }
        require(problem.choices.size == 4 && problem.choices.distinct().size == 4 && problem.answer in problem.choices) { "Provide four distinct choices including the answer" }
        require(problem.choices.all { Regex("0|[1-9]\\d{0,64}").matches(it) }) { "Choices must be canonical nonnegative integers" }
        if (level == "hard" || level == "very_hard") {
            val delta = (if (level == "hard") 10 else 3).toBigInteger()
            val values = problem.choices.map { it.toBigInteger() }
            require(problem.choices.all { it.length == problem.answer.length } && values.all { (it - result).abs() <= delta }) { "Choices must be close and have the answer's digit count" }
            val ten = 10.toBigInteger()
            val low = if (problem.answer.length == 1) zero else ten.pow(problem.answer.length - 1)
            val high = ten.pow(problem.answer.length) - java.math.BigInteger.ONE
            require((result == low || values.any { it < result }) && (result == high || values.any { it > result })) { "Include choices above and below the answer where possible" }
        }
        return problem
    }
}
