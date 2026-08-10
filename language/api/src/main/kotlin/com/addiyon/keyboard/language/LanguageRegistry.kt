package com.addiyon.keyboard.language

class LanguageRegistry(
    packs: List<LanguagePack>,
    val defaultLanguageId: LanguageId
) {
    private val orderedPacks = packs.toList()
    private val byId = orderedPacks.associateBy(LanguagePack::id)

    init {
        require(orderedPacks.isNotEmpty())
        require(byId.size == orderedPacks.size)
        require(defaultLanguageId in byId)
    }

    var activeLanguageId: LanguageId = defaultLanguageId
        private set

    val activePack: LanguagePack
        get() = requireNotNull(byId[activeLanguageId])

    val installedPacks: List<LanguagePack>
        get() = orderedPacks

    fun resolveSaved(savedId: String?): LanguageId =
        savedId
            ?.let { runCatching { LanguageId.of(it) }.getOrNull() }
            ?.takeIf(byId::containsKey)
            ?: defaultLanguageId

    fun restore(savedId: String?): LanguageId {
        activeLanguageId = resolveSaved(savedId)
        return activeLanguageId
    }

    fun activate(
        id: LanguageId,
        beforeChange: (outgoing: LanguagePack) -> Unit = {},
        afterChange: (incoming: LanguagePack) -> Unit = {}
    ): Boolean {
        if (id == activeLanguageId) return false
        val incoming = byId[id] ?: return false
        val outgoing = activePack
        beforeChange(outgoing)
        activeLanguageId = id
        afterChange(incoming)
        return true
    }

    fun activateNext(
        beforeChange: (outgoing: LanguagePack) -> Unit = {},
        afterChange: (incoming: LanguagePack) -> Unit = {}
    ): Boolean {
        if (orderedPacks.size < 2) return false
        val currentIndex = orderedPacks.indexOfFirst { it.id == activeLanguageId }
        val next = orderedPacks[(currentIndex + 1).mod(orderedPacks.size)]
        return activate(next.id, beforeChange, afterChange)
    }

    fun contains(id: LanguageId): Boolean = id in byId
}
