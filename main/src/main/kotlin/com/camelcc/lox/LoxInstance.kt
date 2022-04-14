package com.camelcc.lox

abstract class LoxInstance(open val name: String) {
    private val fields = mutableMapOf<String, Any?>()

    fun get(name: Token): Any? {
        if (fields.containsKey(name.lexeme)) {
            return fields[name.lexeme]
        }

        val method = findMethod(name.lexeme)
        if (method != null) {
            return method.bind(this)
        }

        throw RuntimeError(name, "Undefined property '${name.lexeme}'.")
    }

    abstract fun findMethod(name: String): LoxFunction?

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    override fun toString() = "$name instance"
}