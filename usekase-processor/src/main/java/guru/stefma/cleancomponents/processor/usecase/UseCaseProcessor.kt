package guru.stefma.cleancomponents.processor.usecase

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeAliasSpec
import guru.stefma.cleancomponents.annotation.UseCase
import guru.stefma.cleancomponents.usecase.*
import me.eugeniomarletti.kotlin.metadata.KotlinClassMetadata
import me.eugeniomarletti.kotlin.metadata.KotlinMetadataUtils
import me.eugeniomarletti.kotlin.metadata.extractFullName
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import org.jetbrains.kotlin.serialization.ClassData
import org.jetbrains.kotlin.serialization.ProtoBuf
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement

@AutoService(Processor::class)
class UseCaseProcessor : KotlinAbstractProcessor(), KotlinMetadataUtils {

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        roundEnv.getElementsAnnotatedWith(UseCase::class.java).forEach {
            if (it.kind != ElementKind.CLASS) {
                return false
            }

            val className = it.simpleName.toString()
            val classPackage = elementUtils.getPackageOf(it).toString()
            val fullName = it.fullName()
            val documentation = extractDocumentation(it)

            val generatedClass = GeneratedClass(messager, className, classPackage, fullName, documentation)
            createTypeAlias(generatedClass)
        }

        return true
    }

    private fun createTypeAlias(generatedClass: GeneratedClass) = generatedDir?.also {
        val file = FileSpec.builder(generatedClass.classPackage, generatedClass.fileName)
                .addTypeAlias(
                        TypeAliasSpec.builder(generatedClass.className, generatedClass.typeNameFromGenerics)
                                .addKdoc(generatedClass.documentation)
                                .build()
                )
                .build()

        it.mkdirs()
        file.writeTo(it)
    }

    /**
     * Reads the documentation of the given element and returns it as a String.
     *
     * The `*` at the beginning of each line of the documentation are also being read, therefore
     * this method removes any leading `*` from the read documentation.
     */
    private fun extractDocumentation(element: Element): String? {
        return elementUtils.getDocComment(element)
                ?.replace(" * ", "")
                ?.replace(" *\n", "\n")
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return setOf(UseCase::class.java.name).toMutableSet()
    }
}

// Workaround section.
// See https://github.com/square/kotlinpoet/issues/236

/**
 * Returns the "fullname" of this [Element] first superclass (see [ProtoBuf.Class.supertypeList[0]]).
 *
 * The name will be (for example):
 * ```
 *     guru.stefma.cleancomponents.usecase.ObservableUseCase<kotlin.Array<kotlin.String>, kotlin.Boolean>
 * ```
 */
private fun Element.fullName(): String {
    val metadata = kotlinMetadata as KotlinClassMetadata
    val proto = metadata.data.classProto
    val name = proto.findUseCase(metadata.data)
    return name.replace("`", "").replace("`", "")
}

private fun ProtoBuf.Class.findUseCase(classData: ClassData): String {
    val foundUseCase = supertypeList.find {
        val fullName = it.extractFullName(classData)
        fullName.contains(SingleUseCase::class.java.simpleName)
                || fullName.contains(CompletableUseCase::class.java.simpleName)
                || fullName.contains(MaybeUseCase::class.java.simpleName)
                || fullName.contains(ObservableUseCase::class.java.simpleName)
                || fullName.contains(RxUseCase::class.java.simpleName)
                || fullName.contains(guru.stefma.cleancomponents.usecase.UseCase::class.java.simpleName)
    }

    return foundUseCase?.extractFullName(classData) ?: throw IllegalArgumentException("You don't implement a UseCase!")
}