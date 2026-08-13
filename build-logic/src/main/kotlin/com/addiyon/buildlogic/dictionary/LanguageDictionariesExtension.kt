package com.addiyon.buildlogic.dictionary

import javax.inject.Inject
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

abstract class LanguageDictionarySpec @Inject constructor(
    val name: String,
) {
    abstract val wordsDat: RegularFileProperty
    abstract val lexemesDat: RegularFileProperty
    abstract val surfaceStatsDat: RegularFileProperty
    abstract val ngramsDat: RegularFileProperty
    abstract val ngramAudit: RegularFileProperty
    abstract val outputDb: RegularFileProperty
    abstract val normalization: Property<String>
    abstract val maxPrefixLength: Property<Int>
}

open class LanguageDictionariesExtension @Inject constructor(
    objects: ObjectFactory,
) {
    val dictionaries: NamedDomainObjectContainer<LanguageDictionarySpec> =
        objects.domainObjectContainer(LanguageDictionarySpec::class.java) { name ->
            objects.newInstance(LanguageDictionarySpec::class.java, name)
        }
    val manifestFile: RegularFileProperty = objects.fileProperty()
}
