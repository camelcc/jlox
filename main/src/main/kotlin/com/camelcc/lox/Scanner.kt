package com.camelcc.lox

import error

class Scanner(private val source: String) {
    companion object {
        private val KEY_WORDS = mapOf(
            "and" to TokenType.AND,
            "class" to TokenType.CLASS,
            "else" to TokenType.ELSE,
            "false" to TokenType.FALSE,
            "for" to TokenType.FOR,
            "fun" to TokenType.FUN,
            "if" to TokenType.IF,
            "nil" to TokenType.NIL,
            "or" to TokenType.OR,
            "print" to TokenType.PRINT,
            "return" to TokenType.RETURN,
            "super" to TokenType.SUPER,
            "this" to TokenType.THIS,
            "true" to TokenType.TRUE,
            "var" to TokenType.VAR,
            "while" to TokenType.WHILE,
            "break" to TokenType.BREAK
        )
    }

    private val tokens = mutableListOf<Token>()

    private var start = 0
    private var current = 0
    private var line = 1

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }
        tokens.add(Token(TokenType.EOF, "", null, line))
        return tokens.toList()
    }

    private fun scanToken() {
        when (val c = advance()) {
            '(' -> addToken(TokenType.LEFT_PAREN)
            ')' -> addToken(TokenType.RIGHT_PAREN)
            '{' -> addToken(TokenType.LEFT_BRACE)
            '}' -> addToken(TokenType.RIGHT_BRACE)
            ',' -> addToken(TokenType.COMMA)
            '.' -> addToken(TokenType.DOT)
            '-' -> addToken(TokenType.MINUS)
            '+' -> addToken(TokenType.PLUS)
            ';' -> addToken(TokenType.SEMICOLON)
            '*' -> addToken(TokenType.STAR)
            '?' -> addToken(TokenType.QUESTION)
            ':' -> addToken(TokenType.COLON)
            '!' -> addToken(if (match('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            '=' -> addToken(if (match('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
            '<' -> addToken(if (match('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            '>' -> addToken(if (match('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)
            '/' -> {
                if (match('/')) { // comments
                    while (peek() != '\n' && !isAtEnd()) {
                        advance()
                    }
                } else if (match('*')) { // start comments
                    while (!(peek() == '*' && peekNext() == '/') && !isAtEnd()) {
                        if (peek() == '\n') {
                            line++
                        }
                        advance()
                    }
                    if (isAtEnd()) {
                        error(line, "Unexpected eof. Comment didn't close.")
                    }
                    advance()
                    advance()
                } else {
                    addToken(TokenType.SLASH)
                }
            }
            ' ', '\t', '\r' -> {}
            '\n' -> line++
            '"' -> string()
            else -> {
                if (c.isDigit()) number()
                else if (c.isAlpha()) identifier()
                else error(line, "Unexpected character.")
            }
        }
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }
        if (isAtEnd()) {
            error(line, "Unterminated string.")
            return
        }
        // closing "
        advance()
        addToken(TokenType.STRING, source.substring(start+1, current-1))
    }

    private fun number() {
        while (peek().isDigit()) {
            advance()
        }
        if (peek() == '.' && peekNext().isDigit()) {
            advance() // consume the .
            while (peek().isDigit()) {
                advance()
            }
        }
        addToken(TokenType.NUMBER, source.substring(start, current).toDouble())
    }

    private fun identifier() {
        while (peek().isAlphaNumeric()) {
            advance()
        }
        val text = source.substring(start, current)
        val tokenType = KEY_WORDS[text] ?: TokenType.IDENTIFIER
        addToken(tokenType)
    }

    private fun addToken(type: TokenType) = addToken(type, null)

    private fun addToken(type: TokenType, literal: Any?) =
        tokens.add(Token(type, source.substring(start, current), literal, line))

    private fun advance() = source[current++]
    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]
    private fun peekNext(): Char = if (current+1 >= source.length) '\u0000' else source[current+1]
    private fun match(expected: Char): Boolean {
        if (isAtEnd() || source[current] != expected) {
            return false
        }
        current++
        return true
    }

    private fun isAtEnd() = current >= source.length
}

fun Char.isAlpha(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_'
fun Char.isAlphaNumeric(): Boolean = this.isDigit() || this.isAlpha()
