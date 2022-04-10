package com.camelcc.lox

import com.camelcc.lox.ast.Statement

class LoxFunction(private val declaration: Statement.Function,
                  private val closure: Environment): LoxCallable {
    override fun arity() = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        for (i in declaration.params.indices) {
            environment.define(declaration.params[i].lexeme, arguments[i])
        }
        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (res: Return) {
            return res.value
        }
        return null
    }

    override fun toString() = "<fn ${declaration.name.lexeme}>"
}