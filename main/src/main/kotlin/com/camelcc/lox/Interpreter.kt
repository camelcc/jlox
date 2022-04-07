package com.camelcc.lox

import com.camelcc.lox.ast.Expression
import com.camelcc.lox.ast.Visitor
import runtimeError

class RuntimeError(val token: Token, override val message: String): RuntimeException(message)

class Interpreter: Visitor<Any?> {
    fun interpret(expression: Expression) {
        try {
            val value = evaluate(expression)
            println(stringify(value))
        } catch (error: RuntimeError) {
            runtimeError(error)
        }
    }

    override fun visitTernaryExpression(expr: Expression.Ternary): Any? {
        return if (isTruthy(evaluate(expr.check))) {
            evaluate(expr.trueValue)
        } else {
            evaluate(expr.falseValue)
        }
    }

    override fun visitBinaryExpression(expr: Expression.Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)
        return when (expr.operator.tokenType) {
            TokenType.BANG_EQUAL -> !isEqual(left, right)
            TokenType.EQUAL_EQUAL -> isEqual(left, right)
            TokenType.GREATER -> {
                if (checkOperandsType<Double>(expr.operator, left, right)) {
                    (left as Double) > (right as Double)
                } else if (checkOperandsType<String>(expr.operator, left, right)) {
                    (left as String) > (right as String)
                } else {
                    throw RuntimeError(expr.operator, "Operands must be either numbers or strings")
                }
            }
            TokenType.GREATER_EQUAL -> {
                if (checkOperandsType<Double>(expr.operator, left, right)) {
                    (left as Double) >= (right as Double)
                } else if (checkOperandsType<String>(expr.operator, left, right)) {
                    (left as String) >= (right as String)
                } else {
                    throw RuntimeError(expr.operator, "Operands must be either numbers or strings")
                }
            }
            TokenType.LESS -> {
                if (checkOperandsType<Double>(expr.operator, left, right)) {
                    (left as Double) < (right as Double)
                } else if (checkOperandsType<String>(expr.operator, left, right)) {
                    (left as String) < (right as String)
                } else {
                    throw RuntimeError(expr.operator, "Operands must be either numbers or strings")
                }
            }
            TokenType.LESS_EQUAL -> {
                if (checkOperandsType<Double>(expr.operator, left, right)) {
                    (left as Double) <= (right as Double)
                } else if (checkOperandsType<String>(expr.operator, left, right)) {
                    (left as String) <= (right as String)
                } else {
                    throw RuntimeError(expr.operator, "Operands must be either numbers or strings")
                }
            }
            TokenType.MINUS -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) - (right as Double)
            }
            TokenType.PLUS -> {
                if (checkOperandsType<Double>(expr.operator, left, right)) {
                    (left as Double) + (right as Double)
                } else if (checkOperandsType<String>(expr.operator, left, right)) {
                    (left as String) + (right as String)
                } else if (left is String && right is Double) {
                    left + stringify(right)
                } else {
                    throw RuntimeError(expr.operator, "Operands must be either numbers or strings")
                }
            }
            TokenType.SLASH -> {
                checkNumberOperands(expr.operator, left, right)
                if ((right as Double) == .0) {
                    throw RuntimeError(expr.operator, "can't / by zero")
                }
                (left as Double) / (right as Double)
            }
            TokenType.STAR -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) * (right as Double)
            }
            else -> null
        }
    }

    override fun visitGroupingExpression(expr: Expression.Grouping) =
        evaluate(expr.expr)

    override fun visitLiteralExpression(expr: Expression.Literal) =
        expr.value

    override fun visitUnaryExpression(expr: Expression.Unary): Any? {
        val right = evaluate(expr.right)
        return when (expr.token.tokenType) {
            TokenType.BANG -> !isTruthy(right)
            TokenType.MINUS -> {
                checkNumberOperand(expr.token, right)
                -(right as Double)
            }
            else -> null
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
