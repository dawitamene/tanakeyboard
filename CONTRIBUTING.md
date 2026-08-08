# Contributing to Addiyon Keyboard

## Required design-system gate

Every UI change must begin by reading [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md).
That document is normative for people, LLMs, and coding agents. Choose the
correct surface (`AddiyonBrandTheme` for app chrome or `CustomKeyboardTheme`
for the user-themed IME), use `ui/design` tokens and components, and use
Material semantic roles instead of raw colors. Newly touched app screens use
localized copy, controls must work, and fixed-height IME surfaces must not gain
full-panel scrolling.

If a token, role, component, or theme behavior changes, update the design-system
document and `DesignSystemContractTest` in the same pull request. A visual
exception is acceptable only when it is narrowly justified in the document and
allowed by the contract test.

## Before opening a pull request

Run these commands from any terminal:

```sh
/Users/dev/code/addiyon-keyboard/gradlew testDebugUnitTest --tests "com.addiyon.keyboard.ui.design.DesignSystemContractTest"
/Users/dev/code/addiyon-keyboard/gradlew compileDebugKotlin
git -C /Users/dev/code/addiyon-keyboard diff --check
```

Run focused tests for behavior you changed as well. Do not install or assemble
as a substitute for the repository's normal post-change verification. Follow
the install and timestamped-APK requirements in `AGENTS.md` before handoff.

## Review expectations

Reviewers check the strict checklist at the end of
[`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md), including accessibility,
English/Amharic localization, semantic color use, state handling, and the
distinction between branded app UI and the user-themed keyboard.
