import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
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
            file += "interface Visitor<R> {\n"
            classDeclaration.getSealedSubclasses().forEach {
                file += "    fun visit${it.simpleName.asString()}Expr(expr: ${classDeclaration.simpleName.asString()}.${it.simpleName.asString()}): R\n"
            }
            file += "}\n\n"

            //fun <R> Expr.Binary.accept(visitor: Visitor<R>) {
            //    visitor.visitBinaryExpr(this)
            //}
            classDeclaration.getSealedSubclasses().forEach {
                file += "fun <R> ${classDeclaration.simpleName.asString()}.${it.simpleName.asString()}.accept(visitor: Visitor<R>) {\n"
                file += "    visitor.visit${it.simpleName.asString()}${classDeclaration.simpleName.asString()}(this)\n"
                file += "}\n"
            }
        }
    }
}

class ASTProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ASTProcessor(environment.codeGenerator, environment.logger)
    }
}
