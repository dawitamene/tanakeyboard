## Summary

<!-- What user-visible behavior changed? -->

## Design-system gate

- [ ] I read [`docs/DESIGN_SYSTEM.md`](../docs/DESIGN_SYSTEM.md).
- [ ] I chose the correct surface (`AddiyonBrandTheme` or `CustomKeyboardTheme`).
- [ ] I used existing `ui/design` tokens/components and Material semantic roles.
- [ ] I did not add raw screen colors, arbitrary one-off visual values, or
      hard-coded user-facing strings on newly touched app screens.
- [ ] Any new app copy is localized in English and Amharic.
- [ ] Controls are functional and loading/empty/error/result states are handled.
- [ ] Fixed-height IME surfaces remain non-scrolling and height-stable.
- [ ] Accessibility, touch targets, contrast, font scaling, and Amharic layout
      were checked.
- [ ] If the system changed, I updated `docs/DESIGN_SYSTEM.md` and the contract
      test together.

## Verification

- [ ] `/Users/dev/code/addiyon-keyboard/gradlew testDebugUnitTest --tests "com.addiyon.keyboard.ui.design.DesignSystemContractTest"`
- [ ] `/Users/dev/code/addiyon-keyboard/gradlew compileDebugKotlin`
- [ ] `git -C /Users/dev/code/addiyon-keyboard diff --check`
- [ ] Other focused tests (describe below)

## Notes

<!-- Include screenshots, exceptions, or follow-up work when relevant. -->
