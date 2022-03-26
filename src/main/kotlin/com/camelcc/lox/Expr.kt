package com.camelcc.lox

//@Visitor
sealed class Expr {
//    @Accept
    data class Binary(val left: Expr, val operator: Token, val right: Expr): Expr() {
        override fun <R> accept(visitor: Visitor<R>) = visitor.visitBinaryExpr(this)
    }

    data class Grouping(val expr: Expr): Expr() {
        override fun <R> accept(visitor: Visitor<R>) = visitor.visitGroupingExpr(this)
    }
    data class Literal(val value: Any): Expr() {
        override fun <R> accept(visitor: Visitor<R>) = visitor.visitLiteral(this)
    }

    data class Unary(val token: Token, val right: Expr): Expr() {
        override fun <R> accept(visitor: Visitor<R>) = visitor.visitUnary(this)
    }

    abstract fun <R> accept(visitor: Visitor<R>): R
}

interface Visitor<R> {
    fun visitBinaryExpr(expr: Expr.Binary): R
    fun visitGroupingExpr(expr: Expr.Grouping): R
    fun visitLiteral(expr: Expr.Literal): R
    fun visitUnary(expr: Expr.Unary): R
}
