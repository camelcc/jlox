package com.camelcc.lox

import com.camelcc.lox.ast.Expression
import com.camelcc.lox.ast.Statement
import runtimeError

class RuntimeError(val token: Token, override val message: String): RuntimeException(message)

class Interpreter: Expression.Visitor<Any?>, Statement.Visitor<Any?> {
    private var environment = Environment()

    fun interpret(statements: List<Statement>) {
        try {
            statements.forEach { statement ->
                val value = execute(statement)
                if (statement is Statement.Expr) {
                    println(stringify(value))
                }
            }
        } catch (error: RuntimeError) {
            runtimeError(error)
        }
    }

    private fun execute(statement: Statement) =
        statement.accept(this)

    override fun visitTernaryExpression(expression: Expression.Ternary): Any? {
        return if (isTruthy(evaluate(expression.check))) {
            evaluate(expression.trueValue)
        } else {
            evaluate(expression.falseValue)
        }
    }

    override fun visitBinaryExpression(expression: Expression.Binary): Any? {
        val left = evaluate(expression.left)
        val right = evaluate(expression.right)
        return when (expression.operator.tokenType) {
            TokenType.BANG_EQUAL -> !isEqual(left, right)
            TokenType.EQUAL_EQUAL -> isEqual(left, right)
            TokenType.GREATER -> {
                if (checkOperandsType<Double>(expression.operator, left, right)) {
                    (left as Double) > (right as Double)
                } else if (checkOperandsType<String>(expression.operator, left, right)) {
                    (left as String) > (right as String)
                } else {
                    throw RuntimeError(expression.operator, "Operands must be either numbers or strings")
                }
            }
            TokenType.GREATER_EQUAL -> {
                if (checkOperandsType<Double>(expression.operator, left, right)) {
                    (left as Double) >= (right as Double)
                } else if (checkOperandsType<String>(expression.operator, left, right)) {
                    (left as String) >= (right as String)
                } else {
                    throw RuntimeError(expression.operator, "Operands must be either numbers or strings")
                }
            }
            TokenType.LESS -> {
                if (checkOperandsType<Double>(expression.operator, left, right)) {
                    (left as Double) < (right as Double)
                } else if (checkOperandsType<String>(expression.operator, left, right)) {
                    (left as String) < (right as String)
                } else {
                    throw RuntimeError(expression.operator, "Operands must be either numbers or strings")
                }
            }
            TokenType.LESS_EQUAL -> {
                if (checkOperandsType<Double>(expression.operator, left, right)) {
                    (left as Double) <= (right as Double)
                } else if (checkOperandsType<String>(expression.operator, left, right)) {
                    (left as String) <= (right as String)
                } else {
                    throw RuntimeError(expression.operator, "Operands must be either numbers or strings")
                }
            }
            TokenType.MINUS -> {
                checkNumberOperands(expression.operator, left, right)
                (left as Double) - (right as Double)
            }
            TokenType.PLUS -> {
                if (checkOperandsType<Double>(expression.operator, left, right)) {
                    (left as Double) + (right as Double)
                } else if (checkOperandsType<String>(expression.operator, left, right)) {
                    (left as String) + (right as String)
                } else if (left is String && right is Double) {
                    left + stringify(right)
                } else {
                    throw RuntimeError(expression.operator, "Operands must be either numbers or strings")
                }
            }
            TokenType.SLASH -> {
                checkNumberOperands(expression.operator, left, right)
                if ((right as Double) == .0) {
                    throw RuntimeError(expression.operator, "can't / by zero")
                }
                (left as Double) / (right as Double)
            }
            TokenType.STAR -> {
                checkNumberOperands(expression.operator, left, right)
                (left as Double) * (right as Double)
            }
            else -> null
        }
    }

    override fun visitGroupingExpression(expression: Expression.Grouping) =
        evaluate(expression.expr)

    override fun visitLiteralExpression(expression: Expression.Literal) =
        expression.value

    override fun visitUnaryExpression(expression: Expression.Unary): Any? {
        val right = evaluate(expression.right)
        return when (expression.token.tokenType) {
            TokenType.BANG -> !isTruthy(right)
            TokenType.MINUS -> {
                checkNumberOperand(expression.token, right)
                -(right as Double)
            }
            else -> null
        }
    }

    override fun visitVariableExpression(expression: Expression.Variable) =
        environment.get(expression.name)

    override fun visitAssignExpression(expression: Expression.Assign): Any? {
        val value = evaluate(expression.expr)
        environment.assign(expression.name, value)
        return value
    }

    override fun visitExprStatement(statement: Statement.Expr): Any? {
        return evaluate(statement.expr)
    }

    override fun visitPrintStatement(statement: Statement.Print): Any? {
        val value = evaluate(statement.expr)
        println(stringify(value))
        return null
    }

    override fun visitVarStatement(statement: Statement.Var): Any? {
        val value = statement.initializer?.let {
            evaluate(statement.initializer)
        }
        environment.define(statement.name.lexeme, value)
        return null
    }

    override fun visitBlockStatement(statement: Statement.Block): Any? {
        executeBlock(statement.statements, Environment(environment))
        return null
    }

    private fun executeBlock(statements: List<Statement>, env: Environment) {
        val popEnvironment = environment
        try {
            environment = env
            for (statement in statements) {
                execute(statement)
            }
        } finally {
            environment = popEnvironment
        }
    }

    private fun isTruthy(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (obj is Boolean) {
            return obj
        }
        return true
    }

    private fun isEqual(left: Any?, right: Any?): Boolean {
        if (left == null && right == null) {
            return true
        }
        if (left == null) {
            return false
        }
        return left == right
    }

    private fun stringify(obj: Any?): String {
        if (obj == null) {
            return "nil"
        }
        if (obj is Double) {
            if (obj.toInt().toDouble() == obj) {
                return obj.toInt().toString()
            }
            return obj.toString()
        }

        return obj.toString()
    }

    private fun evaluate(expr: Expression) =
        expr.accept(this)

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand is Double) return
        throw RuntimeError(operator, "Operand must be a number.")
    }

    private inline fun <reified T> checkOperandsType(operator: Token, left: Any?, right: Any?) =
        left is T && right is T

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (left is Double && right is Double) return
        throw RuntimeError(operator, "Operands must be a number.")
    }
}
