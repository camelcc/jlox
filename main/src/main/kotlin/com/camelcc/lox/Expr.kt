package com.camelcc.lox

import com.camelcc.lox.ast.ASTVisitor
import com.camelcc.lox.ast.Expression
import com.camelcc.lox.ast.Visitor

@ASTVisitor
sealed class Expr {
    data class Ternary(val check: Expr, val trueValue: Expr, val falseValue: Expr): Expr()
    data class Binary(val left: Expr, val operator: Token, val right: Expr): Expr()
    data class Grouping(val expr: Expr): Expr()
    data class Literal(val value: Any?): Expr()
    data class Unary(val token: Token, val right: Expr): Expr()
}

class ASTPrinter: Visitor<String> {
    fun print(expr: Expression): String = expr.accept(this)

    override fun visitTernaryExpression(expr: Expression.Ternary) =
        parenthesize("ternary", expr.check, expr.trueValue, expr.falseValue)

    override fun visitBinaryExpression(expr: Expression.Binary): String =
        parenthesize(expr.operator.lexeme, expr.left, expr.right)

    override fun visitGroupingExpression(expr: Expression.Grouping): String =
        parenthesize("group", expr.expr)

    override fun visitLiteralExpression(expr: Expression.Literal): String {
        if (expr.value == null) return "nil"
        return expr.value.toString()
    }

    override fun visitUnaryExpression(expr: Expression.Unary): String =
        parenthesize(expr.token.lexeme, expr.right)

    private fun parenthesize(name: String, vararg exprs: Expression): String {
        val sb = StringBuilder()
        sb.append("(").append(name)
        for (expr in exprs) {
            sb.append(" ")
            sb.append(expr.accept(this))
        }
        sb.append(")")
        return sb.toString()
    }
}
