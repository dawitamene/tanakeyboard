package com.addiyon.keyboard.language.english

import com.addiyon.keyboard.suggestion.CandidateRanker

object EnglishContractions {
    private const val DEFAULT_CONTRACTION_FREQUENCY = 500_000

    private val CONTRACTIONS: Map<String, List<String>> = mapOf(
        "im" to listOf("I'm"),
        "ill" to listOf("I'll"),
        "id" to listOf("I'd"),
        "ive" to listOf("I've"),
        "youre" to listOf("you're"),
        "youve" to listOf("you've"),
        "youd" to listOf("you'd"),
        "youll" to listOf("you'll"),
        "hes" to listOf("he's"),
        "hed" to listOf("he'd"),
        "hell" to listOf("he'll"),
        "shes" to listOf("she's"),
        "shed" to listOf("she'd"),
        "shell" to listOf("she'll"),
        "itll" to listOf("it'll"),
        "its" to listOf("it's"),
        "were" to listOf("we're"),
        "weve" to listOf("we've"),
        "wed" to listOf("we'd"),
        "well" to listOf("we'll"),
        "theyre" to listOf("they're"),
        "theyve" to listOf("they've"),
        "theyd" to listOf("they'd"),
        "theyll" to listOf("they'll"),
        "thatll" to listOf("that'll"),
        "thats" to listOf("that's"),
        "thatd" to listOf("that'd"),
        "therell" to listOf("there'll"),
        "theres" to listOf("there's"),
        "thered" to listOf("there'd"),
        "heres" to listOf("here's"),
        "hered" to listOf("here'd"),
        "herell" to listOf("here'll"),
        "whats" to listOf("what's"),
        "whatll" to listOf("what'll"),
        "whatd" to listOf("what'd"),
        "whatve" to listOf("what've"),
        "wheres" to listOf("where's"),
        "wherell" to listOf("where'll"),
        "whered" to listOf("where'd"),
        "whens" to listOf("when's"),
        "whenll" to listOf("when'll"),
        "whend" to listOf("when'd"),
        "whys" to listOf("why's"),
        "whyll" to listOf("why'll"),
        "whyd" to listOf("why'd"),
        "hows" to listOf("how's"),
        "howll" to listOf("how'll"),
        "howd" to listOf("how'd"),
        "whos" to listOf("who's"),
        "wholl" to listOf("who'll"),
        "whod" to listOf("who'd"),
        "whove" to listOf("who've"),
        "cant" to listOf("can't"),
        "wont" to listOf("won't"),
        "shant" to listOf("shan't"),
        "dont" to listOf("don't"),
        "doesnt" to listOf("doesn't"),
        "didnt" to listOf("didn't"),
        "isnt" to listOf("isn't"),
        "arent" to listOf("aren't"),
        "wasnt" to listOf("wasn't"),
        "werent" to listOf("weren't"),
        "havent" to listOf("haven't"),
        "hasnt" to listOf("hasn't"),
        "hadnt" to listOf("hadn't"),
        "couldnt" to listOf("couldn't"),
        "shouldnt" to listOf("shouldn't"),
        "wouldnt" to listOf("wouldn't"),
        "mustnt" to listOf("mustn't"),
        "neednt" to listOf("needn't"),
        "darent" to listOf("daren't"),
        "mightnt" to listOf("mightn't"),
        "oughtnt" to listOf("oughtn't"),
        "aint" to listOf("ain't"),
        "lets" to listOf("let's"),
        "couldve" to listOf("could've"),
        "shouldve" to listOf("should've"),
        "wouldve" to listOf("would've"),
        "mightve" to listOf("might've"),
        "mustve" to listOf("must've"),
        "wouldntve" to listOf("wouldn't've"),
        "shouldntve" to listOf("shouldn't've"),
        "couldntve" to listOf("couldn't've"),
        "mustntve" to listOf("mustn't've"),
        "maam" to listOf("ma'am"),
        "oclock" to listOf("o'clock")
    )

    fun contractionsFor(key: String): List<String> =
        CONTRACTIONS[key] ?: emptyList()

    fun candidatesFor(
        key: String,
        frequencyOf: (String) -> Int?
    ): List<CandidateRanker.DictionaryWord> {
        val words = CONTRACTIONS[key] ?: return emptyList()
        return words.map { word ->
            val frequency = frequencyOf(word) ?: DEFAULT_CONTRACTION_FREQUENCY
            CandidateRanker.DictionaryWord(word, frequency)
        }
    }
}
