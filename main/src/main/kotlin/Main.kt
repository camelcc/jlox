import com.camelcc.lox.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.exp
import kotlin.system.exitProcess

val interpreter = Interpreter()

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
    if (hadRuntimeError) {
        exitProcess(70)
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
    val parser = Parser(tokens)
    val statements = parser.parse()

    if (hasError) {
        return
    }

    interpreter.interpret(statements)
}

var hasError = false
var hadRuntimeError = false

fun error(line: Int, message: String) {
    report(line = line, where = "", message = message)
}

fun report(line: Int, where: String, message: String) {
    System.err.println("[line $line] Error$where: $message")
    hasError = true
}

fun runtimeError(error: RuntimeError) {
    System.err.println("${error.message}\n[line ${error.token.line}]")
    hadRuntimeError = true
}
