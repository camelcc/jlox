package com.camelcc.lox

import com.camelcc.lox.ast.Expression
import com.camelcc.lox.ast.Statement
import report

class Parser(private val tokens: List<Token>) {
    class ParseError : RuntimeException()

    private var current: Int = 0
    private var whileDepth: Int = 0

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

    // declaration    → classDecl
    //                | funDecl
    //                | varDecl
    //                | statement ;
    private fun declaration(): Statement? {
        try {
            if (match(TokenType.CLASS)) return classDeclaration()
            if (match(TokenType.FUN)) return function("function")
            if (match(TokenType.VAR)) return varDeclaration()
            return statement()
        } catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    // classDecl      → "class" IDENTIFIER ( "<" IDENTIFIER )?
    //                  "{" function* "}" ;
    private fun classDeclaration(): Statement {
        val name = consume(TokenType.IDENTIFIER, "Expect class name.")

        var superClass: Expression.Variable? = null
        if (match(TokenType.LESS)) {
            consume(TokenType.IDENTIFIER, "Expect superclass name.")
            superClass = Expression.Variable(previous)
        }

        consume(TokenType.LEFT_BRACE, "Expect '{' before class body.")
        val methods = mutableListOf<Statement.Function>()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd) {
            methods.add(function("method"))
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.")
        return Statement.Class(name, superClass, methods)
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
    //                | forStmt
    //                | ifStmt
    //                | printStmt
    //                | returnStmt
    //                | whileStmt
    //                | breakStmt
    //                | block ;
    private fun statement(): Statement {
        if (match(TokenType.FOR)) return forStatement()
        if (match(TokenType.IF)) return ifStatement()
        if (match(TokenType.PRINT)) return printStatement()
        if (match(TokenType.RETURN)) return returnStatement()
        if (match(TokenType.WHILE)) return whileStatement()
        if (match(TokenType.BREAK)) return breakStatement()
        if (match(TokenType.LEFT_BRACE)) return Statement.Block(block())
        return expressionStatement()
    }

    // ifStmt         → "if" "(" expression ")" statement
    //               ( "else" statement )? ;
    private fun ifStatement(): Statement {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.")
        val thenStatement = statement()
        val elseStatement = if (match(TokenType.ELSE)) statement() else null
        return Statement.If(condition, thenStatement, elseStatement)
    }

    // whileStmt      → "while" "(" expression ")" statement ;
    private fun whileStatement(): Statement {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.")
        whileDepth++
        val body = statement()
        whileDepth--
        return Statement.While(condition, body)
    }

    private fun breakStatement(): Statement {
        val keyword = previous
        if (whileDepth == 0) {
            error(peek, "Unexpected 'break' without for/while loop")
        }
        consume(TokenType.SEMICOLON, "Expect ';' after break.")
        return Statement.Break(keyword)
    }

    // forStmt        → "for" "(" ( varDecl | exprStmt | ";" )
    //                  expression? ";"
    //                  expression? ")" statement ;
    private fun forStatement(): Statement {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.")
        val initializer = if (match(TokenType.SEMICOLON)) {
            null
        } else if (match(TokenType.VAR)) {
            varDeclaration()
        } else {
            expressionStatement()
        }
        val condition = if (!check(TokenType.SEMICOLON)) expression() else Expression.Literal(true)
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition.")
        val increment = if (!check(TokenType.RIGHT_PAREN)) expression() else null
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.")
        whileDepth++
        var body = statement()
        whileDepth--
        if (increment != null) {
            body = Statement.Block(listOf(body, Statement.Expr(increment)))
        }
        body = Statement.While(condition, body)
        if (initializer != null) {
            body = Statement.Block(listOf(initializer, body))
        }
        return body
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

    // returnStmt     → "return" expression? ";" ;
    private fun returnStatement(): Statement {
        val keyword = previous
        val value = if (!check(TokenType.SEMICOLON)) expression() else null
        consume(TokenType.SEMICOLON, "Expect ';' after return value.")
        return Statement.Return(keyword, value)
    }

    // exprStmt       → expression ";" ;
    private fun expressionStatement(): Statement {
        val value = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value.")
        return Statement.Expr(value)
    }

    // funDecl        → "fun" function ;
    // function       → IDENTIFIER "(" parameters? ")" block ;
    private fun function(kind: String): Statement.Function {
        // consume class static function
        if (match(TokenType.CLASS)) { }
        val name = consume(TokenType.IDENTIFIER, "Expect $kind name")
        consume(TokenType.LEFT_PAREN, "Expect '(' after $kind name.")
        val parameters = mutableListOf<Token>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255) {
                    error(peek, "Can't have more than 255 parameters.")
                }

                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."))
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")

        consume(TokenType.LEFT_BRACE, "Expect '{' before $kind body.")
        val body = block()
        return Statement.Function(name, parameters, body)
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

    // assignment     → ( call "." )? IDENTIFIER "=" assignment
    //                | ternary
    //                | logic_or ;
    private fun assignment(): Expression {
        val expr = ternary()
        if (match(TokenType.EQUAL)) {
            val equals = previous
            val value = assignment()
            if (expr is Expression.Variable) {
                val name = expr.name
                return Expression.Assign(name, value)
            } else if (expr is Expression.Get) {
                return Expression.Set(expr.obj, expr.name, value)
            }
            error(equals, "Invalid assignment target.")
        }
        return expr
    }

    // ternary        → logic_or ? expression : ternary | logic_or
    private fun ternary(): Expression {
        var expr = or()
        if (match(TokenType.QUESTION)) {
            val trueExpr = expression()
            consume(TokenType.COLON, "Expect ':' for ternary operation")
            val falseExpr = ternary()
            expr = Expression.Ternary(expr, trueExpr, falseExpr)
        }
        return expr
    }

    // logic_or       → logic_and ( "or" logic_and )* ;
    private fun or(): Expression {
        var expr = and()
        while (match(TokenType.OR)) {
            val operator = previous
            val right = and()
            expr = Expression.Logical(expr, operator, right)
        }
        return expr
    }

    // logic_and      → equality ( "and" equality )* ;
    private fun and(): Expression {
        var expr = equality()
        while (match(TokenType.AND)) {
            val operator = previous
            val right = equality()
            expr = Expression.Logical(expr, operator, right)
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
    //                | call;
    private fun unary(): Expression {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val operator = previous
            val right = unary()
            return Expression.Unary(operator, right)
        }
        return call()
    }

    // call           → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
    private fun call(): Expression {
        var expr = primary()
        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                expr = finishCall(expr)
            } else if (match(TokenType.DOT)) {
                val name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.")
                expr = Expression.Get(expr, name)
            } else {
                break
            }
        }
        return expr
    }

    // arguments      → expression ( "," expression )* ;
    private fun finishCall(callee: Expression): Expression {
        val arguments = mutableListOf<Expression>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (arguments.size >= 255) {
                    error(peek, "Can't have more than 255 arguments.")
                }
                // Can't using expression since we add comma expression here
                arguments.add(assignment())
            } while (match(TokenType.COMMA))
        }
        val paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.")
        return Expression.Call(callee, paren, arguments)
    }

    // primary        → "true" | "false" | "nil" | "this"
    //                | NUMBER | STRING | IDENTIFIER | "(" expression ")"
    //                | "super" "." IDENTIFIER ;
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

        if (match(TokenType.SUPER)) {
            val keyword = previous
            consume(TokenType.DOT, "Expect '.' after 'super'.")
            val method = consume(TokenType.IDENTIFIER, "Expect superclass method name.")
            return Expression.Super(keyword, method)
        }

        if (match(TokenType.THIS)) {
            return Expression.This(previous)
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