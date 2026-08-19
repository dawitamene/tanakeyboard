# Personal Dictionary: Language Tabs & Built-in Word Filtering

## Overview

This update addresses two key requirements for the Personal Dictionary in Addiyon Keyboard and TextRevamp:

1. **Separation of Language Tabs in Addiyon Keyboard**:
   - In Addiyon Keyboard (which supports Amharic, English, and Afaan Oromo), learned words should not be mixed together in a single flat list.
   - The Personal Dictionary screen (`KeyboardPersonalDictionaryScreen`) will feature separate tabs for each language (e.g., **Amharic / አማርኛ**, **English**, and **Afaan Oromoo**).
   - TextRevamp (English-only) will display its words cleanly without multi-language tab clutter (or single tab).

2. **Preventing Built-In / Dictionary Words from Being Added**:
   - Words that already exist in the built-in system dictionary / lexicon (e.g., standard words like "the", "hello", "ሰላም", etc.) should **not** be learned or stored in the Personal Dictionary.
   - Storing standard dictionary words wastes slots in the 512-word personal dictionary limit (`MAX_WORDS = 512`) and clutters the user's personal dictionary settings list with ordinary vocabulary.
   - The Personal Dictionary should only store user-specific custom words (names, unique vocabulary, emails, slang, unlisted inflections).

---

## 1. Preventing Built-in Dictionary Words from Being Learned

### Write-Path Filtering (`rememberWord`)
In `PackKeyboardService.kt`:
- Before calling `personalDictionary.learn(languageId, word, ...)`, check if the active language's suggestion engine already contains/recognizes the word:
  ```kotlin
  if (activeSuggestionEngine.isReady && activeSuggestionEngine.containsWord(word)) {
      return // Built-in word, do not store in personal dictionary
  }
  ```
- Also, if the word is already learned in `PersonalDictionary`, `learn` updates its frequency count without creating a duplicate.

### Engine Support (`containsWord`)
Extend `LanguageSuggestionEngine` with `fun containsWord(word: String): Boolean`:
- **`EnglishSuggestionEngine`**:
  - Checks `dictionary.frequencyOf(word) != null` and known contractions.
- **`AmharicSuggestionEngine`**:
  - Checks `dictionary.frequencyOf(word) != null`, `morphLexicon.contains(...)`, or `verbLexicon.contains(...)`.
- **`OromoSuggestionEngine`**:
  - Returns `false` (or checks basic set if available).

---

## 2. Language Tabs in Personal Dictionary Screen

### Data Model Updates in `PersonalDictionary`
- Add helper methods to query words partitioned by language bucket:
  - `fun wordsForLanguage(languageId: String): List<String>`
  - `fun availableLanguages(): List<String>` (returns bucket keys that have entries)
  - `fun clearLanguage(languageId: String)` to clear words only for the selected language/tab, or `clearAll()` for everything.

### UI Changes in `KeyboardPersonalDictionaryScreen.kt`
- Display a tab bar (`TabRow` / `PrimaryTabRow` / pill chips) when multiple language tabs are available (e.g. Amharic, English, Afaan Oromoo in Addiyon).
- When a tab is selected:
  - The word list shows only words for that language bucket.
  - The item count ("X words") reflects the active tab.
  - "Clear" can clear the active tab or provide clear-all with confirmation.
  - If a tab has no words, show the empty state message for that specific tab.
- If only one language is supported/present (e.g. TextRevamp), the tab bar is omitted or displays as a single unified view.

---

## Affected Files

1. `suggestions/api/src/main/kotlin/com/addiyon/keyboard/suggestion/LanguageSuggestionEngine.kt`
   - Add `fun containsWord(word: String): Boolean = false`
2. `language/english/src/main/java/com/addiyon/keyboard/language/english/EnglishSuggestionEngine.kt`
   - Implement `containsWord` using `dictionary.frequencyOf`
3. `language/amharic/src/main/java/com/addiyon/keyboard/language/amharic/AmharicSuggestionEngine.kt`
   - Implement `containsWord` using `dictionary.frequencyOf` & morphology checks
4. `keyboard/runtime/src/main/java/com/addiyon/keyboard/PackKeyboardService.kt`
   - In `rememberWord`, skip learning if `activeSuggestionEngine.containsWord(word)`
5. `suggestions/core/src/main/kotlin/com/addiyon/keyboard/suggestion/PersonalDictionary.kt`
   - Add `wordsForLanguage(languageId)`, `clearLanguage(languageId)`, `languagesWithWords()`
6. `features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/KeyboardPersonalDictionaryScreen.kt`
   - Add tabs for language filtering in Addiyon Keyboard
7. Unit tests & contract tests:
   - `suggestions/core/src/test/kotlin/com/addiyon/keyboard/suggestion/PersonalDictionaryTest.kt`
   - `apps/textrevamp/src/test/java/com/addiyon/keyboard/ui/design/DesignSystemContractTest.kt`
