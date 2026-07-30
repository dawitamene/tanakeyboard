# Final Physical-Phone Test Checklist

Run this checklist on the exact release build intended for production. Test only the installed app and keyboard; no Play Console work is included.

## Test record

- Build/version: ____________________
- Phone model: ____________________
- Android version: ____________________
- Test date: ____________________
- Tester: ____________________

## Release rule

- [ ] **No blockers** — No crash, ANR, frozen/blank screen, missing keyboard, corrupted text, or private-field data leak occurred.
- [ ] **Failures recorded** — Every failed item has reproduction steps, the screen/app used, and a screenshot or recording where useful.

## 1. Fresh install and onboarding

- [ ] **Fresh launch** — Clear app data or install fresh; the app opens to onboarding without a crash or blank frame.
- [ ] **App identity** — The Addiyon name, logo, text, and buttons are sharp, readable, and not clipped.
- [ ] **English app language** — Select English; all onboarding text changes and remains readable.
- [ ] **Amharic app language** — Select Amharic; all onboarding text changes and remains readable.
- [ ] **Open keyboard settings** — Tap the activation button; Android's keyboard settings open successfully.
- [ ] **Return after enabling** — Enable Addiyon and return; onboarding advances to the keyboard-selection step.
- [ ] **Open keyboard picker** — Tap the switch button; the Android keyboard picker opens.
- [ ] **Select Addiyon** — Choose Addiyon; the app detects it as default without needing a restart.
- [ ] **All-set screen** — The confirmation appears once and advances without freezing.
- [ ] **Feature tour Next** — Move through all tour pages; indicators, animations, examples, and buttons work.
- [ ] **Feature tour Skip** — On a fresh-data rerun, Skip exits the tour and opens the main settings screen.
- [ ] **Tour persistence** — Close and reopen the app; the completed/skipped tour does not repeat.
- [ ] **Enabled but not default** — Select another keyboard, reopen Addiyon, and confirm it returns to the correct selection step.
- [ ] **Disabled after setup** — Disable Addiyon, reopen the app, and confirm it safely returns to activation onboarding.

## 2. Main Activity and navigation

- [ ] **Main screen load** — The settings home opens quickly with no flicker, overlap, or missing cards.
- [ ] **App language toggle** — Switch English/Amharic on the main screen; every visible label updates immediately.
- [ ] **Language persistence** — Relaunch the app; the selected app-interface language remains selected.
- [ ] **Themes navigation** — Open Themes, use the top back button, and confirm it returns to the main screen.
- [ ] **Typing Guide navigation** — Open the guide, use the top back button, and confirm it returns to the main screen.
- [ ] **Preferences navigation** — Open Preferences, use the top back button, and confirm it returns to the main screen.
- [ ] **Test Keyboard navigation** — Open Test Keyboard, then return; both the top and Android back actions behave correctly.
- [ ] **About navigation** — Open About and return with both the top and Android back actions.
- [ ] **System back behavior** — From every child screen, Android back returns one level; from the main screen it exits the app.
- [ ] **Rapid navigation** — Open and close each child screen five times; no duplicate screen, freeze, or crash appears.
- [ ] **Background restore** — Open a child screen, background the app for one minute, and return; the same screen is usable.
- [ ] **Rotation/recreation** — Rotate the phone on the main and a child screen; layout and navigation state remain valid.
- [ ] **Small-screen scrolling** — On the smallest available display/font setting, all rows remain reachable.
- [ ] **Large text** — Increase Android font/display size; important text and controls remain readable and tappable.
- [ ] **Light/dark system mode** — Switch system light/dark mode while the app is open; colors update without unreadable text.

## 3. Themes

- [ ] **Theme list** — All theme groups and preview cards load and scroll smoothly.
- [ ] **Theme selection marker** — Tap several themes; only the current theme shows as selected.
- [ ] **Live keyboard theme** — Select a theme, open the keyboard, and confirm tray, normal keys, special keys, and accent key use it.
- [ ] **Theme persistence** — Force-close and reopen the app and keyboard; the chosen theme remains active.
- [ ] **Minimal theme modes** — Check one Minimal theme in system light and dark modes; both variants have clear contrast.
- [ ] **Colored theme contrast** — Check one Pastel, Bold, Nature, Vibrant, and Dark theme; every key label is readable.
- [ ] **Tana effect** — Select Tana; its background effect renders smoothly without flashing or slowing key taps.
- [ ] **Toolbar theme shortcut** — Open Themes from the keyboard toolbar, choose a theme, and confirm it returns directly to typing with the new theme.

## 4. Preferences

- [ ] **Vibration on** — Enable vibration and type several keys; each press gives a short, consistent vibration.
- [ ] **Vibration off** — Disable vibration; ordinary keypresses no longer vibrate.
- [ ] **Sound on** — Enable keypress sound and type; sound plays once per press at a reasonable level.
- [ ] **Sound off** — Disable keypress sound; ordinary keypresses are silent.
- [ ] **Whole-row toggles** — Tap both the label area and switch for each preference; each changes exactly once.
- [ ] **Number row on** — Enable Number Row; digits appear above both letter layouts and type correctly.
- [ ] **Number row off** — Disable Number Row; it disappears without leaving empty space.
- [ ] **Preference persistence** — Relaunch the app and keyboard; vibration, sound, and number-row choices remain saved.
- [ ] **Keyboard height slider** — Drag the slider from minimum to maximum; the percentage and preview update smoothly.
- [ ] **Keyboard height handle** — Drag the preview's top edge up and down; it follows the finger and stays within limits.
- [ ] **Keyboard preview match** — The height preview matches the selected language, number-row setting, and theme.
- [ ] **Minimum height typing** — Save minimum height and type on the real keyboard; all rows remain visible and tappable.
- [ ] **Maximum height typing** — Save maximum height and type; the keyboard fits the screen and does not cover its own controls.
- [ ] **Height persistence** — Restart the keyboard and phone app; the chosen height remains active.
- [ ] **Height back path** — Back from Keyboard Height returns to Preferences, not directly to the main screen.

## 5. Guide, test field, About, sharing, and feedback

- [ ] **Guide content** — Scroll from top to bottom; all Amharic examples, headings, and diagrams render correctly.
- [ ] **Guide interactions** — Expand/tap/drag interactive guide content; it responds without accidental navigation.
- [ ] **Guide toolbar shortcut** — Open the guide from the keyboard toolbar; back returns directly to the typing app.
- [ ] **Test field auto-open** — Open Test Keyboard; the field focuses and Addiyon appears automatically.
- [ ] **Test field editing** — Enter multiple lines, move the cursor, select text, replace it, and delete it normally.
- [ ] **About identity** — About shows the correct name, logo, installed version, description, privacy text, and creator text.
- [ ] **Share App** — Tap Share App; a system chooser opens with a valid Addiyon link, then cancel safely.
- [ ] **Rate App** — Tap Rate App; an installed store or browser handles the link, or the app shows a graceful error.
- [ ] **Feedback sheet** — Open Feedback, dismiss by swiping/back/outside tap, and reopen it successfully.
- [ ] **Feedback email** — Choose Email; a mail chooser opens with the expected subject, or a graceful error appears.
- [ ] **Feedback Telegram** — Choose Telegram; Telegram/browser opens the intended destination, or a graceful error appears.
- [ ] **Keyboard feedback shortcut** — Open Feedback from the keyboard toolbar; cancel and return to the original field with text intact.

## 6. Keyboard startup, layout, and lifecycle

- [ ] **First keyboard open** — Focus a field after boot/install; the complete keyboard appears without a long blank state.
- [ ] **Repeated open/close** — Open and dismiss the keyboard 20 times; there is no crash, blank keyboard, or growing delay.
- [ ] **App switching** — Type in one app, switch to another, and type immediately; input goes only to the active field.
- [ ] **Field switching** — Move quickly between two fields; text never lands in the previously focused field.
- [ ] **Keyboard switching** — Switch to another IME and back; Addiyon restores a usable layout and current preferences.
- [ ] **Language persistence** — Select English or Amharic, close and reopen the keyboard; the selected mode remains.
- [ ] **Portrait layout** — In portrait, every row and toolbar control is visible, aligned, and tappable.
- [ ] **Landscape layout** — In landscape, no key is clipped and typing remains responsive.
- [ ] **System navigation area** — Gesture/button navigation area matches the keyboard theme and does not cover keys.
- [ ] **Key preview** — Hold character keys; preview balloons are readable, correctly positioned, and disappear on release.
- [ ] **Edge-key preview** — Hold keys at the far left and right; previews stay on-screen.
- [ ] **Cancelled press** — Press a key, drag away, and release; it does not insert an unintended character.
- [ ] **Fast typing** — Type continuously for one minute; characters remain in order with no missed/doubled taps.
- [ ] **Toolbar stability** — Open/close the toolbar repeatedly; it does not resize or corrupt the keyboard.
- [ ] **AI/clipboard placeholders** — Tap the current AI and clipboard toolbar buttons; they cause no crash or unwanted text change.

## 7. English typing

- [ ] **Basic alphabet** — Type all `a-z`; each lowercase letter appears once and in order.
- [ ] **One-shot Shift** — Tap Shift then a letter; one uppercase letter appears and Shift turns off.
- [ ] **Shift cancel** — Tap Shift twice slowly; it returns to lowercase without typing.
- [ ] **Caps Lock** — Double-tap Shift, type several letters, and confirm all are uppercase until Shift is tapped again.
- [ ] **Auto-cap first word** — Start a normal text field; the first English letter is capitalized.
- [ ] **Auto-cap sentence** — Type a sentence, period, and space; the next English letter is capitalized.
- [ ] **Comma/period** — Type punctuation directly after a word; placement is correct and no character disappears.
- [ ] **Space** — Tap Space repeatedly; exactly one space is inserted per tap.
- [ ] **Enter/newline** — In a multiline field, Enter inserts a newline.
- [ ] **Long-press letter** — Long-press a character key; it commits the key's direct character once.
- [ ] **Word backspace** — Type a word and tap Delete repeatedly; one visible character is removed per tap.
- [ ] **Hold Delete** — Hold Delete through a long paragraph; deletion repeats smoothly and stops immediately on release.
- [ ] **Delete cancellation** — Start a Delete press and cancel it by moving away; deletion stops without continuing in the background.
- [ ] **Resume edited word** — Place the cursor inside/after an existing English word and edit it; only that word changes.

## 8. Amharic typing and transliteration

- [ ] **Basic example** — In Amharic mode, type `selam`; the composing text becomes `ሰላም`.
- [ ] **Bare vowels** — Type bare vowel sequences, including `a`; the expected independent vowel forms appear.
- [ ] **Consonant orders** — Type one consonant with `e`, `u`, `i`, `a`, `ie`, and `o`; each produces the intended Fidel order.
- [ ] **Digraphs** — Test `sh`, `ch`, and `gn`; each resolves as a digraph rather than two unrelated Fidel units.
- [ ] **Case-sensitive families** — Compare `h/H`, `t/T`, and `ch/C`; their distinct Fidel families appear.
- [ ] **Shifted previews** — Toggle Shift in Amharic mode; affected corner previews update to the shifted Fidel family.
- [ ] **One-shot Amharic Shift** — Type one shifted Amharic key; Shift turns off after the key.
- [ ] **Amharic Caps Lock** — Double-tap Shift and type affected keys; shifted families persist until unlocked.
- [ ] **Amharic punctuation** — Tap comma and period; they commit `፣` and `።`.
- [ ] **Commit on space** — Type an Amharic word then Space; the word remains correct and composition ends cleanly.
- [ ] **Commit on mode change** — Change language or number mode mid-word; the completed Fidel stays intact.
- [ ] **Fidel-unit delete** — Type a multi-letter transliteration such as `she`, then Delete; the whole rendered Fidel unit is removed.
- [ ] **Mixed text** — Type Amharic, switch language, type English/numbers, then switch back; order and spacing remain correct.
- [ ] **Cursor edit** — Move the cursor into existing Amharic text and type/delete; surrounding Fidel is not corrupted.
- [ ] **Long-press Amharic key** — Long-press a key with a Fidel corner preview; the displayed Fidel commits once.
- [ ] **Unknown sequence safety** — Type a long unusual Latin sequence quickly; the keyboard stays responsive and preserves usable output.

## 9. Suggestions and predictions

- [ ] **English suggestions** — Type a common English prefix; relevant suggestions appear without blocking typing.
- [ ] **English typo correction** — Type a small misspelling; a sensible near-match appears.
- [ ] **English suggestion tap** — Tap a suggestion; it replaces the active word once with correct spacing/case.
- [ ] **English next-word prediction** — Commit a common word and Space; next-word predictions appear and insert correctly.
- [ ] **Amharic alternatives** — Type an ambiguous transliteration; alternate Fidel readings appear.
- [ ] **Amharic completion** — Type a common Amharic prefix; useful completions appear and insert correctly.
- [ ] **Amharic next-word prediction** — Commit a common Fidel word and Space; next-word predictions appear and insert correctly.
- [ ] **Suggestion after delete** — Type, delete, and retype quickly; suggestions match the current text, not stale text.
- [ ] **Rapid language switch** — Switch languages repeatedly while suggestions load; no old-language suggestions leak into the new mode.
- [ ] **Long input performance** — Type a very long/non-dictionary word; keypresses remain responsive and suggestions do not freeze the UI.
- [ ] **No suggestion overwrite** — Ignore suggestions and keep typing; the app never changes the word by itself.
- [ ] **Selection safety** — Select existing text, type a word, and tap a suggestion; only the intended selection/current word changes.
- [ ] **Email suggestions** — In an email field, type a local part and `@`; domain chips appear and complete the whole address correctly.

## 10. Language, number, symbol, and keypad modes

- [ ] **Language key** — Tap the language key; Amharic and English layouts alternate once per tap.
- [ ] **Space swipe left** — Swipe left across Space; the language changes without inserting a space.
- [ ] **Space swipe right** — Swipe right across Space; the language changes without inserting a space.
- [ ] **Short space drag** — Make a small horizontal drag; it does not accidentally change language.
- [ ] **Number mode entry** — Tap the number toggle from each language; the standard number page opens.
- [ ] **Number keys** — Type every digit and the common punctuation/operators; each symbol matches its label.
- [ ] **Amharic number page** — From Amharic number mode, cycle to Ethiopic numerals/punctuation and test representative keys.
- [ ] **Symbol page cycle** — Cycle through number, Ethiopic/symbol, and more-symbol pages; each toggle reaches the expected page.
- [ ] **More symbols** — Test brackets, slash, backslash, copyright, trademark, music, math, quotes, and ellipsis.
- [ ] **Return to letters** — Tap the letter toggle from every numeric/symbol page; it returns directly to the active language.
- [ ] **Language in symbols** — Change language from a symbol page; the mode and resulting layout remain consistent.
- [ ] **Phone-style keypad** — Open the keypad from the number page; digits and available controls type correctly.
- [ ] **Exit keypad** — Use the keypad symbol toggle; it returns to the full number/symbol page.
- [ ] **Numeric field auto-layout** — Focus phone, number, and date/time fields; a suitable numeric keypad opens automatically.
- [ ] **Number-row interaction** — With Number Row enabled, use it in both languages and during emoji search; digits insert in the intended target.

## 11. Emoji

- [ ] **Emoji open** — Open emoji from the toolbar; the panel replaces the keyboard cleanly without changing window height.
- [ ] **Initial loading** — On first open, any loading state resolves to the emoji grid without a crash.
- [ ] **Category tabs** — Tap every category; the grid jumps to the correct section.
- [ ] **Emoji scrolling** — Scroll rapidly from top to bottom and back; cells load smoothly with no blank/stuck grid.
- [ ] **Emoji commit** — Tap emoji from several categories; each inserts once at the cursor.
- [ ] **Emoji after word** — Open emoji while composing a word; the word commits first and the emoji lands after it.
- [ ] **Skin tone** — Long-press a supported emoji, choose each style of tone, and confirm the chosen variant inserts.
- [ ] **Skin-tone persistence** — Close/reopen the panel and keyboard; the selected tone remains for that emoji.
- [ ] **Recents** — Use several emoji, reopen the panel, and confirm they appear in Recents in a sensible order.
- [ ] **Emoji search** — Search a common English term; relevant results appear and tapping one inserts it.
- [ ] **No-result search** — Search nonsense text; a clear empty state appears without lag.
- [ ] **Search editing** — Move the query cursor, select text, clear it, and use Delete; only the search query changes.
- [ ] **Search Shift/numbers** — Use Shift and the number row while searching; the query updates correctly.
- [ ] **Exit search** — Return from search to browse; category browsing remains usable.
- [ ] **Emoji Delete** — Use the emoji panel's Delete key; it removes the preceding text/emoji safely.
- [ ] **Emoji back/close** — Close emoji with its back control; the normal keyboard returns in the prior language.

## 12. Voice input

- [ ] **First permission request** — Tap the microphone with permission unset; Android asks for microphone permission once.
- [ ] **Permission grant** — Grant permission; listening starts and the keyboard remains visible/stable.
- [ ] **Permission denial** — On a fresh-data rerun, deny permission; a clear state appears and ordinary typing still works.
- [ ] **English dictation** — In English mode, dictate a short sentence; partial/final text is inserted once in English.
- [ ] **Amharic dictation** — In Amharic mode, dictate a short phrase; recognition uses Amharic and inserts it once.
- [ ] **Pause/resume** — Tap the microphone while listening to pause, then resume; visible text is not lost or duplicated.
- [ ] **Exit voice mode** — Use the voice back arrow; dictation stops and normal suggestions/keys return.
- [ ] **Type during voice mode** — Press a normal key while listening/paused; voice mode ends cleanly and the key is inserted once.
- [ ] **Switch mode during voice** — Change language, open emoji, or open numbers during voice mode; dictation stops safely.
- [ ] **Silence/network error** — Trigger silence or disable connectivity; a recoverable message appears and the keyboard does not crash.
- [ ] **Repeated voice sessions** — Start/stop voice input ten times; no stuck microphone state or duplicated result remains.

## 13. Field types, actions, and privacy

- [ ] **Password field** — Focus a password field; suggestions, email chips, key previews, and microphone are hidden/disabled as intended.
- [ ] **Password isolation** — Type a unique password-like string, leave the field, and confirm it never appears as a suggestion elsewhere.
- [ ] **Visible/web password** — Repeat the privacy check in visible-password and browser password fields.
- [ ] **Email Latin output** — In an email field, typing stays Latin even if the keyboard was in Amharic mode.
- [ ] **Email capitalization** — An email field starts lowercase and does not auto-capitalize the address.
- [ ] **URI field** — A URL field does not auto-capitalize or transliterate the address unexpectedly.
- [ ] **Single-line Done** — In a field labeled Done, the Enter key performs Done and does not insert a newline.
- [ ] **Search action** — In a search field, the Enter key triggers Search once.
- [ ] **Send action** — In a message field labeled Send, the Enter key triggers Send once.
- [ ] **Next/Previous actions** — In a multi-field form, Next and Previous move focus in the expected direction.
- [ ] **Go action** — In a URL/navigation field, Go performs the field action once.
- [ ] **Multiline override** — In a multiline editor, Enter inserts a newline even when the host app has other actions.
- [ ] **Read-only/no editor** — Tap a non-editable area; Addiyon does not appear or crash.
- [ ] **Fresh diagnostics defaults** — Clear app data and open Addiyon; Analytics and
  Crash diagnostics are independently shown and both start off.
- [ ] **Diagnostics persistence** — Enable each option separately, recreate the activity
  and process, and confirm only the selected choices persist on this device.
- [ ] **Consent is not restored** — Restore/transfer app settings to another device;
  diagnostics choices return to off while ordinary keyboard settings may restore.
- [ ] **Analytics privacy** — With Analytics enabled and Firebase DebugView open, type,
  Delete, press Space/Enter, move/select text, use email suggestions, dictate, and repeat
  in a password field; no content-bearing or private-field custom event appears.
- [ ] **Analytics revocation** — Turn Analytics off; future safe actions produce no
  events and local Analytics state resets.
- [ ] **Sanitized non-fatal** — With Crash diagnostics enabled, run the debug-only
  non-fatal command; Firebase shows only the fixed category/coarse class and sanitized
  Addiyon/framework frames, with no original message, cause, or suppressed exceptions.
- [ ] **Crash revocation** — Queue a report, turn Crash diagnostics off, relaunch, and
  confirm the queued report was deleted and no future report uploads.
- [ ] **Minified fatal deobfuscation** — On an internal minified build, run the
  debug/internal controlled fatal command, relaunch, and confirm the source file and line
  are deobfuscated with no typed/editor data.
- [ ] **Release telemetry surface** — Inspect the merged release manifest/AAB: production
  Firebase resources are present; `AD_ID`, AdServices permissions, debug receivers, and
  controlled crash commands are absent.

## 14. Cross-app compatibility

- [ ] **Messages/chat** — Type, edit, suggest, emoji, voice, and send in a messaging app.
- [ ] **Notes/document** — Type a long mixed-language, multiline note and edit text in the middle.
- [ ] **Browser** — Test address, search, email, and password fields.
- [ ] **Email client** — Test recipient address, subject, and multiline body fields.
- [ ] **Phone/dialer** — Test a phone-number field and confirm the keypad and Delete behave correctly.
- [ ] **Web form** — Test text, number, email, password, Next, and Done actions in one form.
- [ ] **Copy/paste selection** — Use the host app's select/copy/paste controls; Addiyon resumes typing at the correct cursor.
- [ ] **Autofill interaction** — Accept an Android autofill suggestion, then continue typing; text and cursor remain correct.

## 15. Stress, recovery, and performance

- [ ] **Cold app launch** — Force-stop Addiyon, open it, and confirm the first usable screen appears promptly.
- [ ] **Cold keyboard launch** — Force-stop Addiyon, focus a field, and confirm the keyboard recovers without a blank/crash loop.
- [ ] **Cold database preparation** — Clear app data and measure English and Amharic first
  database install/open separately; record `Addiyon.database_install_open` and
  time-to-usable keyboard, with typing never blocked by preparation.
- [ ] **Prediction latency** — On the recorded reference phone run both English and
  Amharic prediction benchmarks; each warm request-to-publication p95 is below 80 ms.
- [ ] **Firebase startup delta** — On the same phone compare cold app/keyboard and
  first-key p50/p95 before and after the production Firebase config is enabled; record
  the delta and reject a material regression.
- [ ] **Rapid key stress** — Tap alternating keys as fast as possible for 30 seconds; no freeze, ANR, or missing burst occurs.
- [ ] **Language stress** — Switch language 20 times while typing; no crash, stale layout, or wrong-language suggestion remains.
- [ ] **Mode stress** — Cycle letters, numbers, symbols, keypad, emoji, and back 20 times; layout remains correct.
- [ ] **Settings stress** — Change theme, height, number row, sound, and vibration while the keyboard is active; updates apply safely.
- [ ] **Large text stress** — Edit near the end and middle of a document with several thousand characters; typing and Delete remain responsive.
- [ ] **Long Delete stress** — Hold Delete across hundreds of characters; it stops on release and the keyboard remains responsive.
- [ ] **Low-memory return** — Open several memory-heavy apps, then return to a text field; Addiyon recreates itself and accepts input.
- [ ] **Screen lock** — Leave a composing word, lock/unlock the phone, and continue; no crash or duplicated/corrupted text occurs.
- [ ] **Incoming interruption** — Interrupt typing with a notification/call overlay, return, and continue in the correct field.
- [ ] **Airplane/offline mode** — Use all non-voice keyboard features offline; typing, transliteration, suggestions, themes, and emoji still work.
- [ ] **Reboot persistence** — Reboot the phone; default-IME status and all saved Addiyon preferences remain correct.
- [ ] **One-hour normal use** — Use Addiyon as the default keyboard across normal apps for at least one hour with no crash, ANR, or worsening lag.

## Final sign-off

- [ ] **Main Activity passed** — Onboarding, settings, guide, themes, preferences, test field, About, sharing, and feedback passed.
- [ ] **Keyboard passed** — English, Amharic, suggestions, numbers/symbols, emoji, voice, field actions, and privacy passed.
- [ ] **Reliability passed** — Lifecycle, cross-app, stress, recovery, and performance checks passed with no release blocker.

Notes/issues:

______________________________________________________________________________

______________________________________________________________________________

______________________________________________________________________________
