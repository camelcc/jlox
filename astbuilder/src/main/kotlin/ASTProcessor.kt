import com.camelcc.lox.ast.ASTGenerator
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*

class ASTProcessor(private val codeGenerator: CodeGenerator, val logger: KSPLogger): SymbolProcessor {
    companion object {
        const val PACKAGE_NAME = "com.camelcc.lox.ast"
    }

    @OptIn(KotlinPoetKspPreview::class, KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val generators = resolver
            .getSymbolsWithAnnotation(ASTGenerator::class.qualifiedName ?: "$PACKAGE_NAME.ASTGenerator")
            .filterIsInstance<KSClassDeclaration>()
        if (!generators.iterator().hasNext()) return emptyList()

        generators.forEach {
            val className = it.getAnnotationsByType(ASTGenerator::class).first().name
            val visitorFileBuilder = FileSpec.builder(PACKAGE_NAME, className)
            it.accept(ASTVisitor(fileSpecBuilder = visitorFileBuilder), Unit)
            visitorFileBuilder.build().writeTo(codeGenerator, true)
        }
        return generators.filterNot { it.validate() }.toList()
    }

    inner class ASTVisitor(private val fileSpecBuilder: FileSpec.Builder): KSVisitorVoid() {
        @OptIn(KotlinPoetKspPreview::class, KspExperimental::class)
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error("Can only annotate class with @ASTVisitor", classDeclaration)
                return
            }
            val classMapping = classDeclaration.getAnnotationsByType(ASTGenerator::class).first().mapping
            val argumentMap = mutableMapOf<String, TypeVariableName>()
            classMapping.forEach { m ->
                val t = m.split(":")
                argumentMap[t[0].trim()] = TypeVariableName(t[1].trim())
            }
            val typeArgumentResolver = object: TypeParameterResolver {
                override val parametersMap: Map<String, TypeVariableName>
                    get() = argumentMap

                override fun get(index: String): TypeVariableName = parametersMap[index]!!
            }

            val className = classDeclaration.getAnnotationsByType(ASTGenerator::class).first().name
            fileSpecBuilder.addImport(classDeclaration.toClassName(), listOf())
            val expressionClass = ClassName(PACKAGE_NAME, className)
            val visitorClass = ClassName(PACKAGE_NAME, "${className}.Visitor").parameterizedBy(TypeVariableName("R"))
            val dataClasses = classDeclaration.getSealedSubclasses().map { dataClass ->
                if (dataClass.classKind == ClassKind.OBJECT) {
                    TypeSpec.objectBuilder(dataClass.toClassName())
                        .addFunction(FunSpec.builder("accept")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter(ParameterSpec("visitor", visitorClass))
                            .addTypeVariable(TypeVariableName("R"))
                            .addStatement("return visitor.visit${dataClass.simpleName.asString()}${className}(this)")
                            .returns(TypeVariableName("R"))
                            .build())
                        .superclass(expressionClass)
                        .addSuperclassConstructorParameter("")
                        .build()
                } else {
                    TypeSpec.classBuilder(dataClass.simpleName.asString())
                        .addModifiers(KModifier.DATA)
                        .primaryConstructor(FunSpec.constructorBuilder()
                            .addParameters(
                                dataClass.getDeclaredProperties().map { property ->
                                    val typeName = property.type.toTypeName(typeArgumentResolver)
                                    ParameterSpec(property.simpleName.asString(), typeName, listOf())
                                }.toList()
                            )
                            .build()
                        )
                        .addProperties(
                            dataClass.getDeclaredProperties().map { property ->
                                val typeName = property.type.toTypeName(typeArgumentResolver)
                                PropertySpec.builder(property.simpleName.asString(), typeName, listOf())
                                    .initializer(property.simpleName.asString())
                                    .build()
                            }.toList()
                        )
                        .addFunction(FunSpec.builder("accept")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter(ParameterSpec("visitor", visitorClass))
                            .addTypeVariable(TypeVariableName("R"))
                            .addStatement("return visitor.visit${dataClass.simpleName.asString()}${className}(this)")
                            .returns(TypeVariableName("R"))
                            .build())
                        .superclass(expressionClass)
                        .addSuperclassConstructorParameter("")
                        .build()
                }
            }.toList()

            val visitor = TypeSpec.interfaceBuilder("Visitor")
                .addTypeVariable(TypeVariableName("R"))
                .addFunctions(dataClasses.map { dataClass ->
                    val clz = ClassName(PACKAGE_NAME, "${className}.${dataClass.name}")
                    FunSpec.builder("visit${dataClass.name}${className}")
                        .addModifiers(KModifier.ABSTRACT)
                        .addParameter(className.lowercase(), clz, listOf())
                        .returns(TypeVariableName("R"))
                        .build()
                })
                .build()

            fileSpecBuilder.addType(TypeSpec.classBuilder(className)
                .addModifiers(KModifier.SEALED)
                .addType(visitor)
                .addTypes(dataClasses)
                .addFunction(FunSpec.builder("accept")
                    .addModifiers(KModifier.ABSTRACT)
                    .addTypeVariable(TypeVariableName("R"))
                    .addParameter(ParameterSpec("visitor", visitorClass))
                    .returns(TypeVariableName("R"))
                    .build())
                .build())
        }
    }
}

class ASTProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ASTProcessor(environment.codeGenerator, environment.logger)
    }
}
