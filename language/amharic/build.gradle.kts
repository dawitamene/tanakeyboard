import com.addiyon.buildlogic.dictionary.GenerateDictionaryDatabase
import com.addiyon.buildlogic.dictionary.LanguageDictionariesExtension

plugins {
    id("addiyon.android.library")
    id("addiyon.language-dictionaries")
}

android {
    namespace = "com.addiyon.keyboard.language.amharic"
}

configure<LanguageDictionariesExtension> {
    manifestFile.set(
        layout.buildDirectory.file("intermediates/dictionaryAssets/amharic_dictionary_manifest.properties")
    )
    dictionaries.register("amharic") {
        wordsDat.set(layout.projectDirectory.file("src/dictionary/amharic_words.dat"))
        lexemesDat.set(layout.projectDirectory.file("src/dictionary/amharic_lexemes.dat"))
        surfaceStatsDat.set(layout.projectDirectory.file("src/dictionary/amharic_surface_stats.dat"))
        ngramsDat.set(layout.projectDirectory.file("src/dictionary/amharic_ngrams.dat"))
        ngramAudit.set(layout.projectDirectory.file("src/dictionary/amharic_ngram_audit.tsv"))
        outputDb.set(layout.buildDirectory.file("intermediates/dictionaryAssets/amharic.db"))
        normalization.set(GenerateDictionaryDatabase.NORMALIZATION_ETHIOPIC)
        maxPrefixLength.set(1)
    }
}

dependencies {
    api(project(":language:api"))
    api(project(":language:android-api"))
    implementation(project(":keyboard:core"))
    implementation(project(":suggestions:core"))
    implementation(project(":suggestions:sqlite"))
    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
}

tasks.configureEach {
    if (name == "testDebugUnitTest") {
        dependsOn("packageDictionaryAssets")
    }
}
