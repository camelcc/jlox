import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import java.io.OutputStream

class ASTProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger): SymbolProcessor {
    operator fun OutputStream.plusAssign(str: String) {
        this.write(str.toByteArray())
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver
            .getSymbolsWithAnnotation("com.camelcc.lox.ast.ASTVisitor")
            .filterIsInstance<KSClassDeclaration>()
        if (!symbols.iterator().hasNext()) return emptyList()

        val visitorFile: OutputStream = codeGenerator.createNewFile(
            // Make sure to associate the generated file with sources to keep/maintain it across incremental builds.
            // Learn more about incremental processing in KSP from the official docs:
            // https://github.com/google/ksp/blob/main/docs/incremental.md
            dependencies = Dependencies(false, *resolver.getAllFiles().toList().toTypedArray()),
            packageName = "com.camelcc.lox.ast",
            fileName = "ExprVisitor")

        visitorFile += "package com.camelcc.lox.ast\n"
        symbols.forEach { it.accept(ASTVisitor(visitorFile), Unit) }

        visitorFile.close()
        return symbols.filterNot { it.validate() }.toList()
    }

    inner class ASTVisitor(private val file: OutputStream): KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error("Can only annotate class with @ASTVisitor", classDeclaration)
                return
            }

            //interface Visitor<R> {
            //    fun visitBinaryExpr(expr: Expr.Binary): R
            //}
            file += "import ${classDeclaration.qualifiedName?.asString()}\n\n"

            file += "sealed class Expression {\n"
            classDeclaration.getSealedSubclasses().forEach { dataClass ->
                file += "    data class ${dataClass.simpleName.asString()}("
                dataClass.getDeclaredProperties().forEach { property ->
                    val type = property.type.resolve()
                    file += "val ${property.simpleName.asString()}: "
                    if (type.declaration == classDeclaration) {
                        file += "Expression"
                    } else {
                        file += type.declaration.qualifiedName?.asString() ?: ""
                        file += if (type.nullability == Nullability.NULLABLE) "?" else ""
                    }
                    file += ", "
                }
                file += "): Expression() {\n"
                file += "        override fun <R> accept(visitor: Visitor<R>): R =\n"
                file += "            visitor.visit${dataClass.simpleName.asString()}Expression(this)\n"
                file += "    }\n"
            }
            file += "    abstract fun <R> accept(visitor: Visitor<R>): R\n"
            file += "}\n\n"

            file += "interface Visitor<R> {\n"
            classDeclaration.getSealedSubclasses().forEach { dataClass ->
                file += "    fun visit${dataClass.simpleName.asString()}Expression(expr: Expression.${dataClass.simpleName.asString()}): R\n"
            }
            file += "}\n\n"
        }
    }
}

class ASTProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ASTProcessor(environment.codeGenerator, environment.logger)
    }
}
