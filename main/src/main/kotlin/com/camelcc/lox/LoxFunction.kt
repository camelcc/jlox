package com.camelcc.lox

import com.camelcc.lox.ast.Statement

class LoxFunction(private val declaration: Statement.Function,
                  private val closure: Environment,
                  private val isInitializer: Boolean): LoxCallable {
    override fun arity() = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        for (i in declaration.params.indices) {
            environment.define(declaration.params[i].lexeme, arguments[i])
        }
        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (res: Return) {
            if (isInitializer) return closure.getAt(0, "this")
            return res.value
        }
        if (isInitializer) return closure.getAt(0, "this")
        return null
    }

    fun bind(instance: LoxInstance): LoxFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return LoxFunction(declaration, environment, isInitializer)
    }

    override fun toString() = "<fn ${declaration.name.lexeme}>"
}