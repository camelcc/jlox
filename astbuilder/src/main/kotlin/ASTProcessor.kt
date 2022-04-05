import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

class ASTProcessor(private val codeGenerator: CodeGenerator, val logger: KSPLogger): SymbolProcessor {
    @OptIn(KotlinPoetKspPreview::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver
            .getSymbolsWithAnnotation("com.camelcc.lox.ast.ASTVisitor")
            .filterIsInstance<KSClassDeclaration>()
        if (!symbols.iterator().hasNext()) return emptyList()

        val visitorFileBuilder = FileSpec.builder("com.camelcc.lox.ast", "ExprVisitor")
        symbols.forEach { it.accept(ASTVisitor(fileSpecBuilder = visitorFileBuilder), Unit) }
        visitorFileBuilder.build().writeTo(codeGenerator, true)
        return symbols.filterNot { it.validate() }.toList()
    }

    inner class ASTVisitor(private val fileSpecBuilder: FileSpec.Builder): KSVisitorVoid() {
        @OptIn(KotlinPoetKspPreview::class)
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error("Can only annotate class with @ASTVisitor", classDeclaration)
                return
            }

            fileSpecBuilder.addImport(classDeclaration.toClassName(), listOf())

            val expressionClass = ClassName("com.camelcc.lox.ast", "Expression")
            val visitorClass = ClassName("com.camelcc.lox.ast", "Visitor").parameterizedBy(TypeVariableName("R"))
            val dataClasses = classDeclaration.getSealedSubclasses().map {  dataClass ->
                TypeSpec.classBuilder(dataClass.simpleName.asString())
                    .addModifiers(KModifier.DATA)
                    .primaryConstructor(FunSpec.constructorBuilder()
                        .addParameters(
                            dataClass.getDeclaredProperties().map { property ->
                                val type = property.type.resolve()
                                val typeClassName = if (type.declaration == classDeclaration) {
                                    expressionClass
                                } else {
                                    type.toClassName().copy(nullable = type.nullability == Nullability.NULLABLE)
                                }
                                ParameterSpec(property.simpleName.asString(), typeClassName, listOf())
                            }.toList()
                        )
                        .build()
                    )
                    .addProperties(
                        dataClass.getDeclaredProperties().map { property ->
                            val type = property.type.resolve()
                            val typeClassName = if (type.declaration == classDeclaration) {
                                expressionClass
                            } else {
                                type.toClassName().copy(nullable = type.nullability == Nullability.NULLABLE)
                            }
                            PropertySpec.builder(property.simpleName.asString(), typeClassName, listOf())
                                .initializer(property.simpleName.asString())
                                .build()
                        }.toList()
                    )
                    .addFunction(FunSpec.builder("accept")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter(ParameterSpec("visitor", visitorClass))
                        .addTypeVariable(TypeVariableName("R"))
                        .addStatement("return visitor.visit${dataClass.simpleName.asString()}Expression(this)")
                        .returns(TypeVariableName("R"))
                        .build())
                    .superclass(expressionClass)
                    .addSuperclassConstructorParameter("")
                    .build()
            }.toList()

            fileSpecBuilder.addType(TypeSpec.classBuilder("Expression")
                .addModifiers(KModifier.SEALED)
                .addTypes(dataClasses)
                .addFunction(FunSpec.builder("accept")
                    .addModifiers(KModifier.ABSTRACT)
                    .addTypeVariable(TypeVariableName("R"))
                    .addParameter(ParameterSpec("visitor", visitorClass))
                    .returns(TypeVariableName("R"))
                    .build())
                .build())

            val visitor = TypeSpec.interfaceBuilder("Visitor")
                .addTypeVariable(TypeVariableName("R"))
                .addFunctions(dataClasses.map { dataClass ->
                    val className = ClassName("com.camelcc.lox.ast", "Expression.${dataClass.name}")
                    FunSpec.builder("visit${dataClass.name}Expression")
                        .addModifiers(KModifier.ABSTRACT)
                        .addParameter("expr", className, listOf())
                        .returns(TypeVariableName("R"))
                        .build()
                })
                .build()
            fileSpecBuilder.addType(visitor)
        }
    }
}

class ASTProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ASTProcessor(environment.codeGenerator, environment.logger)
    }
}
