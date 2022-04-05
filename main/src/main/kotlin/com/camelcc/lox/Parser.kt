package com.camelcc.lox

import com.camelcc.lox.ast.Expression
import report

class Parser(private val tokens: List<Token>) {
    class ParseError : RuntimeException()

    private var current: Int = 0

    fun parse(): Expression? {
        return try {
            expression()
        } catch (error: ParseError) {
            null
        }
    }

    private val isAtEnd: Boolean
        get() = peek.tokenType == TokenType.EOF
    private val peek: Token
        get() = tokens[current]
    private val previous: Token
        get() = tokens[current-1]

    private fun check(tokenType: TokenType) =
        if (isAtEnd) false else peek.tokenType == tokenType

    private fun advance(): Token {
        if (!isAtEnd) {
            current++
        }
        return previous
    }

    // expression     → equality ;
    private fun expression() = comma()

    // comma          → expression ( , expression )* ;
    private fun comma(): Expression {
        var expr = ternary()
        while (match(TokenType.COMMA)) {
            expr = comma()
        }
        return expr
    }

    // ternary        → equality ? expression : ternary | equality
    private fun ternary(): Expression {
        var expr = equality()
        if (match(TokenType.QUESTION)) {
            val trueExpr = expression()
            consume(TokenType.COLON, "Expect ':' for ternary operation")
            val falseExpr = ternary()
            expr = Expression.Ternary(expr, trueExpr, falseExpr)
        }
        return expr
    }

    // equality       → comparison ( ( "!=" | "==" ) comparison )* ;
    private fun equality(): Expression {
        val error = peek.tokenType in listOf(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)
        var expr = if (error) Expression.Literal(null) else comparison()
        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            val operator = previous
            val right = comparison()
            expr = Expression.Binary(expr, operator, right)
        }
        if (error) {
            throw error((expr as Expression.Binary).operator, "Expect left operator hand")
        }
        return expr
    }

    // comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    private fun comparison(): Expression {
        val error = peek.tokenType in listOf(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)
        var expr = if (error) Expression.Literal(null) else term()
        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val operator = previous
            val right = term()
            expr = Expression.Binary(expr, operator, right)
        }
        if (error) {
            throw error((expr as Expression.Binary).operator, "Expect left operator hand")
        }
        return expr
    }

    // term           → factor ( ( "-" | "+" ) factor )* ;
    private fun term(): Expression {
        val error = peek.tokenType in listOf(TokenType.PLUS, TokenType.MINUS)
        var expr = if (error) Expression.Literal(null) else factor()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val operator = previous
            val right = factor()
            expr = Expression.Binary(expr, operator, right)
        }
        if (error) {
            throw error((expr as Expression.Binary).operator, "Expect left operator hand")
        }
        return expr
    }

    // factor         → unary ( ( "/" | "*" ) unary )* ;
    private fun factor(): Expression {
        val error = peek.tokenType in listOf(TokenType.SLASH, TokenType.STAR)
        var expr = if (error) Expression.Literal(null) else unary()
        while (match(TokenType.SLASH, TokenType.STAR)) {
            val operator = previous
            val right = unary()
            expr = Expression.Binary(expr, operator, right)
        }
        if (error) {
            throw error((expr as Expression.Binary).operator, "Expect left operator hand")
        }
        return expr
    }

    // unary          → ( "!" | "-" ) unary
    // | primary ;
    private fun unary(): Expression {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val operator = previous
            val right = unary()
            return Expression.Unary(operator, right)
        }
        return primary()
    }

    // primary        → NUMBER | STRING | "true" | "false" | "nil"
    // | "(" expression ")" ;
    private fun primary(): Expression {
        if (match(TokenType.TRUE)) {
            return Expression.Literal(true)
        }
        if (match(TokenType.FALSE)) {
            return Expression.Literal(false)
        }
        if (match(TokenType.NIL)) {
            return Expression.Literal(null)
        }
        if (match(TokenType.NUMBER, TokenType.STRING)) {
            return Expression.Literal(previous.literal)
        }
        if (match(TokenType.LEFT_PAREN)) {
            val expr = expression()
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
            return Expression.Grouping(expr)
        }
        throw error(peek, "Expect expression.")
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun consume(tokenType: TokenType, message: String): Token {
        if (check(tokenType)) {
            return advance()
        }
        throw error(peek, message)
    }

    private fun error(token: Token, message: String): ParseError {
        if (token.tokenType == TokenType.EOF) {
            report(token.line, " at end", message)
        } else {
            report(token.line, " at '" + token.lexeme.toString() + "'", message)
        }
        return ParseError()
    }

    private fun synchronize() {
        advance()

        while (!isAtEnd) {
            if (previous.tokenType == TokenType.SEMICOLON) {
                return
            }
            when (previous.tokenType) {
                TokenType.CLASS, TokenType.FUN, TokenType.VAR, TokenType.FOR, TokenType.IF, TokenType.WHILE, TokenType.PRINT, TokenType.RETURN -> return
                else -> advance()
            }
        }
    }
}