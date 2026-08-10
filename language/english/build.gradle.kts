import com.addiyon.buildlogic.dictionary.GenerateDictionaryDatabase
import com.addiyon.buildlogic.dictionary.LanguageDictionariesExtension

plugins {
    id("addiyon.android.library")
    id("addiyon.language-dictionaries")
}

android {
    namespace = "com.addiyon.keyboard.language.english"
}

configure<LanguageDictionariesExtension> {
    manifestFile.set(layout.projectDirectory.file("src/main/assets/english_dictionary_manifest.properties"))
    dictionaries.register("english") {
        wordsDat.set(layout.projectDirectory.file("src/main/assets/english_words.dat"))
        ngramsDat.set(layout.projectDirectory.file("src/main/assets/english_ngrams.dat"))
        outputDb.set(layout.projectDirectory.file("src/main/assets/english.db"))
        normalization.set(GenerateDictionaryDatabase.NORMALIZATION_LATIN_LOWERCASE)
        maxPrefixLength.set(2)
    }
}

dependencies {
    api(project(":language:api"))
    implementation(project(":keyboard:core"))
    implementation(project(":suggestions:core"))
    implementation(project(":suggestions:sqlite"))
    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
