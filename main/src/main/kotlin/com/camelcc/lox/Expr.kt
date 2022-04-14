package com.camelcc.lox

import com.camelcc.lox.ast.ASTGenerator
import com.camelcc.lox.ast.Expression

@ASTGenerator(name = "Expression", mapping = ["E:Expression"])
sealed class Expr {
    data class Assign<E>(val name: Token, val expr: E): Expr()
    data class Ternary<E>(val check: E, val trueValue: E, val falseValue: E): Expr()
    data class Binary<E>(val left: E, val operator: Token, val right: E): Expr()
    data class Call<E>(val callee: E, val paren: Token, val arguments: List<E>): Expr()
    data class Get<E>(val obj: E, val name: Token): Expr()
    data class Grouping<E>(val expr: E): Expr()
    data class Literal(val value: Any?): Expr()
    data class Logical<E>(val left: E, val operator: Token, val right: E): Expr()
    data class Set<E>(val obj: E, val name: Token, val value: E): Expr()
    data class This(val keyword: Token): Expr()
    data class Unary<E>(val token: Token, val right: E): Expr()
    data class Variable(val name: Token): Expr()
}

@ASTGenerator(name = "Statement", mapping = ["E:Expression", "S:Statement", "F:Function"])
sealed class Stat {
    data class Block<S>(val statements: List<S>): Stat()
    data class Class<F>(val name: Token, val methods: List<F>): Stat()
    data class Expr<E>(val expr: E): Stat()
    data class Function<S>(val name: Token, val params: List<Token>, val body: List<S>): Stat()
    data class If<E, S>(val condition: E, val thenStatement: S, val elseStatement: S?): Stat()
    data class Print<E>(val expr: E): Stat()
    data class Return<E>(val keyword: Token, val value: E?): Stat()
    data class Var<E>(val name: Token, val initializer: E?): Stat()
    data class While<E, S>(val condition: E, val body: S): Stat()
    data class Break(val keyword: Token): Stat()
}
