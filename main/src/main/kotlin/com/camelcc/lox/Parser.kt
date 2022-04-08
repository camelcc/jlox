package com.camelcc.lox

import com.camelcc.lox.ast.Expression
import com.camelcc.lox.ast.Statement
import report

class Parser(private val tokens: List<Token>) {
    class ParseError : RuntimeException()

    private var current: Int = 0

    fun parse(): List<Statement> {
        val statements = mutableListOf<Statement>()
        while (!isAtEnd) {
            declaration()?.also {
                statements.add(it)
            }
        }
        return statements
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

    /* program        → statement* EOF ; */

    // declaration    → varDecl
    //                | statement ;
    private fun declaration(): Statement? {
        try {
            if (match(TokenType.VAR)) {
                return varDeclaration()
            }
            return statement()
        } catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    // varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
    private fun varDeclaration(): Statement {
        val name = consume(TokenType.IDENTIFIER, "Expect variable name.")
        val initializer = if (match(TokenType.EQUAL)) {
            expression()
        } else null
        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.")
        return Statement.Var(name, initializer)
    }

    // statement      → exprStmt
    //                | printStmt ;
    //                | block ;
    private fun statement(): Statement {
        if (match(TokenType.PRINT)) {
            return printStatement()
        }
        if (match(TokenType.LEFT_BRACE)) {
            return Statement.Block(block())
        }
        return expressionStatement()
    }

    // block          → "{" declaration* "}" ;
    private fun block(): List<Statement> {
        val statements = mutableListOf<Statement>()
        while (!isAtEnd && !check(TokenType.RIGHT_BRACE)) {
            declaration()?.let {
                statements.add(it)
            }
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after block")
        return statements
    }

    // printStmt      → "print" expression ";" ;
    private fun printStatement(): Statement {
        val value = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value.")
        return Statement.Print(value)
    }

    // exprStmt       → expression ";" ;
    private fun expressionStatement(): Statement {
        val value = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value.")
        return Statement.Expr(value)
    }

    // expression     → assignment ;
    private fun expression() = comma()

    // comma          → expression ( , expression )* ;
    private fun comma(): Expression {
        var expr = assignment()
        while (match(TokenType.COMMA)) {
            expr = comma()
        }
        return expr
    }

    // assignment     → IDENTIFIER "=" assignment
    //                | ternary;
    private fun assignment(): Expression {
        val expr = ternary()
        if (match(TokenType.EQUAL)) {
            val equals = previous
            val value = assignment()
            if (expr is Expression.Variable) {
                val name = expr.name
                return Expression.Assign(name, value)
            }
            error(equals, "Invalid assignment target.")
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
        if (match(TokenType.IDENTIFIER)) {
            return Expression.Variable(previous)
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