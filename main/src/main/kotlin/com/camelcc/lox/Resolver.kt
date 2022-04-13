package com.camelcc.lox

import com.camelcc.lox.ast.Expression
import com.camelcc.lox.ast.Statement
import error
import java.util.*

enum class FunctionType { NONE, FUNCTION }

class Resolver(private val interpreter: Interpreter):
    Expression.Visitor<Unit>, Statement.Visitor<Unit> {
    private val scopes = Stack<MutableMap<String, Any?>>()
    private var currentFunction = FunctionType.NONE

    override fun visitBlockStatement(statement: Statement.Block) {
        beginScope()
        resolve(statement.statements)
        endScope()
    }

    fun resolve(statements: List<Statement>) {
        for (statement in statements) {
            resolve(statement)
        }
    }

    private fun resolve(statement: Statement) {
        statement.accept(this)
    }

    private fun resolve(expression: Expression) {
        expression.accept(this)
    }

    private fun beginScope() {
        scopes.push(mutableMapOf())
    }

    private fun endScope() {
        scopes.pop()
    }

    override fun visitVarStatement(statement: Statement.Var) {
        declare(statement.name)
        statement.initializer?.also {
            resolve(it)
        }
        define(statement.name)
    }

    private fun declare(name: Token) {
        if (scopes.isEmpty()) return
        val scope = scopes.peek()
        if (scope.containsKey(name.lexeme)) {
            error(name, "Already a variable with this name in this scope.")
        }
        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) return
        scopes.peek()[name.lexeme] = true
    }

    override fun visitVariableExpression(expression: Expression.Variable) {
        if (!scopes.isEmpty() && scopes.peek()[expression.name.lexeme] == false) {
            error(expression.name, "Can't read local variable in its own initializer.")
        }
        resolveLocal(expression, expression.name)
    }

    private fun resolveLocal(expression: Expression, name: Token) {
        for (i in scopes.indices.reversed()) {
            if (scopes[i].containsKey(name.lexeme)) {
                interpreter.resolve(expression, scopes.size-1-i)
            }
        }
    }

    override fun visitAssignExpression(expression: Expression.Assign) {
        resolve(expression.expr)
        resolveLocal(expression, expression.name)
    }

    override fun visitFunctionStatement(statement: Statement.Function) {
        declare(statement.name)
        define(statement.name)

        resolveFunction(statement, FunctionType.FUNCTION)
    }

    private fun resolveFunction(statement: Statement.Function, type: FunctionType) {
        val enclosingFunction = currentFunction
        currentFunction = type

        beginScope()
        for (param in statement.params) {
            declare(param)
            define(param)
        }
        resolve(statement.body)
        endScope()

        currentFunction = enclosingFunction
    }

    override fun visitExprStatement(statement: Statement.Expr) {
        resolve(statement.expr)
    }

    override fun visitIfStatement(statement: Statement.If) {
        resolve(statement.condition)
        resolve(statement.thenStatement)
        statement.elseStatement?.also {
            resolve(it)
        }
    }

    override fun visitPrintStatement(statement: Statement.Print) {
        resolve(statement.expr)
    }

    override fun visitReturnStatement(statement: Statement.Return) {
        if (currentFunction != FunctionType.FUNCTION) {
            error(statement.keyword, "Can't return from top-level code.")
        }

        statement.value?.also {
            resolve(it)
        }
    }

    override fun visitWhileStatement(statement: Statement.While) {
        resolve(statement.condition)
        resolve(statement.body)
    }

    override fun visitBinaryExpression(expression: Expression.Binary) {
        resolve(expression.left)
        resolve(expression.right)
    }

    override fun visitCallExpression(expression: Expression.Call) {
        resolve(expression.callee)
        for (argument in expression.arguments) {
            resolve(argument)
        }
    }

    override fun visitGroupingExpression(expression: Expression.Grouping) {
        resolve(expression.expr)
    }

    override fun visitLiteralExpression(expression: Expression.Literal) {
    }

    override fun visitLogicalExpression(expression: Expression.Logical) {
        resolve(expression.left)
        resolve(expression.right)
    }

    override fun visitUnaryExpression(expression: Expression.Unary) {
        resolve(expression.right)
    }

    override fun visitTernaryExpression(expression: Expression.Ternary) {
        resolve(expression.check)
        resolve(expression.trueValue)
        resolve(expression.falseValue)
    }

    override fun visitBreakStatement(statement: Statement.Break) {
    }
}