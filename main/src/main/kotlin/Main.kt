import com.camelcc.lox.Scanner
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size > 1) {
        println("Usage: jlox [script]")
        exitProcess(64)
    } else if (args.size == 1) {
        runFile(args[0])
    } else {
        runPrompt()
    }
}

private fun runFile(filePath: String) {
    val bytes = Files.readAllBytes(Paths.get(filePath))
    run(String(bytes = bytes, charset = Charset.defaultCharset()))

    if (hasError) {
        exitProcess(65)
    }
}

private fun runPrompt() {
    val ins = InputStreamReader(System.`in`)
    val reader = BufferedReader(ins)
    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        run(line)
        hasError = false
    }
}

private fun run(script: String) {
    val scanner = Scanner(script)
    val tokens = scanner.scanTokens()

    tokens.forEach {
        print(it)
    }
}

var hasError = false

fun error(line: Int, message: String) {
    report(line = line, where = "", message = message)
}

fun report(line: Int, where: String, message: String) {
    System.err.println("[line $line] Error$where: $message")
    hasError = true
}
