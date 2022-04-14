package com.camelcc.lox

class LoxClass(override val name: String, private val methods: Map<String, LoxFunction>): LoxCallable, LoxInstance(name) {
    override fun arity() =
        findMethod("init")?.arity() ?: 0

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        findMethod("init")?.also {
            it.bind(this).call(interpreter, arguments)
        }
        return this
    }

    override fun findMethod(name: String) = methods[name]

    override fun toString() = name
}