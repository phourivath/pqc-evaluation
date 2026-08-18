package io.github.phourivath.pqcevaluation.runner

object BcKotlinRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val output = if (args.isEmpty()) {
            java.nio.file.Path.of("build", "evaluation-result.json")
        } else {
            java.nio.file.Path.of(args[0])
        }
        java.nio.file.Files.createDirectories(output.toAbsolutePath().parent)
        EvaluationRunner(output).run()
        println(output.toAbsolutePath())
    }
}
