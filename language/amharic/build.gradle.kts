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
    manifestFile.set(layout.projectDirectory.file("src/main/assets/amharic_dictionary_manifest.properties"))
    dictionaries.register("amharic") {
        wordsDat.set(layout.projectDirectory.file("src/main/assets/amharic_words.dat"))
        ngramsDat.set(layout.projectDirectory.file("src/main/assets/amharic_ngrams.dat"))
        outputDb.set(layout.projectDirectory.file("src/main/assets/amharic.db"))
        normalization.set(GenerateDictionaryDatabase.NORMALIZATION_ETHIOPIC)
        maxPrefixLength.set(1)
    }
}

dependencies {
    api(project(":language:api"))
    implementation(project(":keyboard:core"))
    implementation(project(":suggestions:core"))
    implementation(project(":suggestions:sqlite"))
    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
}
