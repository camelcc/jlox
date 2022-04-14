package com.camelcc.lox

import com.camelcc.lox.ast.Expression
import com.camelcc.lox.ast.Statement
import error
import java.util.*

enum class FunctionType { NONE, FUNCTION, INITIALIZER, METHOD }
enum class ClassType { NONE, CLASS, SUBCLASS }

class Resolver(private val interpreter: Interpreter):
    Expression.Visitor<Unit>, Statement.Visitor<Unit> {
    private val scopes = Stack<MutableMap<String, Any?>>()
    private var currentFunction = FunctionType.NONE
    private var currentClass = ClassType.NONE

    override fun visitBlockStatement(statement: Statement.Block) {
        beginScope()
        resolve(statement.statements)
        endScope()
    }

    override fun visitClassStatement(statement: Statement.Class) {
        val enclosingClass = currentClass
        currentClass = ClassType.CLASS

        declare(statement.name)
        define(statement.name)

        if (statement.superClass != null && statement.name.lexeme == statement.superClass.name.lexeme) {
            error(statement.superClass.name, "A class can't inherit from itself.")
        }

        if (statement.superClass != null) {
            currentClass = ClassType.SUBCLASS
            resolve(statement.superClass)
        }

        if (statement.superClass != null) {
            beginScope()
            scopes.peek()["super"] = true
        }

        beginScope()
        scopes.peek()["this"] = true
        for (method in statement.methods) {
            var declaration = FunctionType.METHOD
            if (method.name.lexeme == "init") {
                declaration = FunctionType.INITIALIZER
            }

            resolveFunction(method, declaration)
        }
        endScope()

        if (statement.superClass != null) {
            endScope()
        }

        currentClass = enclosingClass
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
        if (currentFunction == FunctionType.NONE) {
            error(statement.keyword, "Can't return from top-level code.")
        }

        statement.value?.also {
            if (currentFunction == FunctionType.INITIALIZER) {
                error(statement.keyword, "Can't return a value from an initializer.")
            }
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

    override fun visitGetExpression(expression: Expression.Get) {
        resolve(expression.obj)
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

    override fun visitSetExpression(expression: Expression.Set) {
        resolve(expression.value)
        resolve(expression.obj)
    }

    override fun visitSuperExpression(expression: Expression.Super) {
        if (currentClass == ClassType.NONE) {
            error(expression.keyword, "Can't use 'super' outside of a class.")
        } else if (currentClass != ClassType.SUBCLASS) {
            error(expression.keyword, "Can't use 'super' in a class with no superclass.")
        }
        resolveLocal(expression, expression.keyword)
    }

    override fun visitThisExpression(expression: Expression.This) {
        if (currentClass == ClassType.NONE) {
            error(expression.keyword, "Can't use 'this' outside of a class.")
        }
        resolveLocal(expression, expression.keyword)
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