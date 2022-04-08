package com.camelcc.lox.ast

@Target(AnnotationTarget.CLASS)
annotation class ASTGenerator(val name: String, val mapping: Array<String>)