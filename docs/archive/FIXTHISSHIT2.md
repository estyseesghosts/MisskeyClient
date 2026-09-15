# Task

Fix the remaining setup-screen, share-sheet, and launcher-icon problems.

This is an implementation task. Complete the work, test it, and keep the changes limited to the problems described here.

Before changing anything:

1. Read and follow `writing_style.md`.
2. Inspect the current setup-screen implementation.
3. Inspect the current share-sheet implementation.
4. Inspect the launcher/adaptive-icon resources and manifest configuration.
5. Compare the current application against all supplied screenshots.
6. Preserve behavior that already works.

Use these files as references:

- `docs/setupscreenBASIC.webp`
- `docs/2sharesheet.jpg`

The supplied current-state screenshots show the defects that still need correction.

Do not redesign these features again. This pass should correct the remaining visual and launcher-resource problems without disturbing working behavior.

# 1. Setup-screen reference

`setupscreenBASIC.webp` is the design reference.

Its important visual properties are:

- Large, simple surfaces.
- Bubble/pill-shaped actions.
- Large rounded logo containers.
- Sparse layouts.
- Clear visual hierarchy.
- Setup-specific typography.
- Strong foreground/background contrast.

The colors visible in the mockup are examples of theme relationships, not fixed colors.

Use Beeline's actual theme system.

Do not invent terms such as "branding colors" or create a separate palette.

# 2. Fix setup foreground colors

The current setup screen has incorrect theme handling.

The supplied screenshots show that:

- The `beeline` text receives an appropriate theme-aware foreground color.
- The bee remains black.
- The subsequent `welcome back` screen also renders important text in black.
- In dark mode, this produces very poor or effectively unreadable contrast.

Fix this consistently.

The bee and setup-screen text must use appropriate theme-aware foreground colors.

In particular:

- On dark surfaces, use the appropriate light foreground color.
- On light surfaces, use the appropriate dark foreground color.
- The bee must adapt with the setup surface instead of remaining permanently black.
- `welcome back`, `<3`, user-entered server text, and other setup-screen text must remain legible in both light and dark themes.

Use the existing theme roles after inspecting the code.

Do not hard-code black or white except where an existing theme token explicitly resolves to those colors.

# 3. Do not change working setup behavior

The current behavior of the following controls is correct:

- `i'm new, what's this?`
- The editable `enter a server` field.
- `next`
- The existing sign-in flow.
- Existing keyboard behavior.
- Existing server-input behavior.

Do not rewrite these behaviors.

This pass is primarily a visual correction.

# 4. Change the setup controls to true pill shapes

The current implementation gets the general controls right, but their shape is wrong.

The supplied current screenshot shows large rounded rectangles.

The mockup uses much stronger pill/bubble geometry.

Correct the shape of:

- `i'm new, what's this?`
- `enter a server`

and any matching setup bubble where the reference clearly uses the same treatment.

The ends should read as semicircular pill ends rather than ordinary rounded-rectangle corners.

The important distinction is:

**Current:**

large rectangle + rounded corners.

**Required:**

bubble/pill shape with a corner radius effectively based on half of the control height.

Do not change:

- Their tap behavior.
- Text-field behavior.
- Focus behavior.
- Keyboard behavior.
- Validation.
- Navigation.
- Existing sizing unless required to make the intended pill geometry work.

The `enter a server` bubble remains the text field itself.

It must not become a button.

# 5. Preserve the setup hierarchy

Do not change the established screen structure merely while fixing colors and shapes.

The initial screen should continue to contain the same major elements:

- `beeline`
- Bee artwork.
- `i'm new, what's this?`
- `sign in`

The returning-user screen should continue to contain:

- `welcome back`
- `<3`
- Editable `enter a server` bubble.
- `next`

The task is to make the existing implementation visually correct and theme-safe, not to restart the setup implementation.

# 6. Share-sheet reference

Use:

`docs/2sharesheet.jpg`

as the sizing and structure reference.

The existing implementation is close enough in composition.

Do not replace it with another share-sheet design.

The reference has:

- A relatively narrow floating outer card.
- Four stacked primary action bubbles.
- One grouped lower bubble.
- Three lower actions inside that single grouped bubble.
- Substantial post content still visible around the sheet.

The current implementation fails mainly because the sheet is much too wide.

# 7. Fix share-sheet width

The supplied current screenshot shows the share sheet occupying almost the full screen width.

This is incorrect.

The reference is visibly narrower and reads as a floating contextual card over the post.

The share sheet must never be wider than:

**two thirds of the available screen width**

Use this as a hard maximum.

The card should remain centered or otherwise positioned consistently with the current design.

It must retain sensible outer margins.

Do not stretch it to full width simply because the device has enough space.

The same principle must work on:

- Normal phones.
- Foldables.
- Large layouts.

On larger displays, do not allow the sheet to become a huge two-thirds-width tablet panel if the current component architecture supports a sensible additional width cap. Inspect the existing responsive system and choose a maintainable solution while preserving the two-thirds maximum.

# 8. Preserve share-sheet structure

Do not change the established action structure.

The four upper actions remain separate bubble-style controls:

- Follow / Unfollow.
- Block.
- Mute.
- Report.

The bottom section remains one shared bubble containing three controls:

- PM.
- Copy.
- Share.

Do not split the lower group into three separate bubbles.

Do not convert the upper actions into generic menu rows.

Continue using the same visual language as Beeline's hashtag bubbles.

# 9. Shorten the lower share labels

Change the bottom labels.

Current:

`Copy link`

Required:

`Copy`

Current:

`Share with another app`

Required:

`Share`

Leave:

`PM`

as-is unless the current localization/resource system requires another equivalent label.

Do not change what these controls do.

`Copy` still copies the post link.

`Share` still invokes the Android system share flow.

# 10. Do not regress share functionality

The share sheet is already close to correct.

Do not rewrite functional action handling merely to fix dimensions or labels.

Verify that all existing actions still work:

- Follow.
- Unfollow.
- Block.
- Mute.
- Report.
- PM.
- Copy.
- Share.

Preserve contextual relationship state.

# 11. Fix the launcher icon

There is a separate application-icon problem.

The supplied installation screenshot demonstrates that Android can display the Beeline icon during APK installation.

However, after installation, the expected icon is not correctly displayed on the launcher/home screen.

Diagnose the actual launcher-resource problem.

Do not assume that because the package installer can display an icon, the launcher configuration is correct.

Inspect:

- Manifest icon declarations.
- `android:icon`.
- `android:roundIcon`, if present.
- `mipmap` resources.
- Adaptive-icon XML.
- Foreground resources.
- Background resources.
- Monochrome/themed-icon resources where supported.
- Density/resource qualifiers.
- Any legacy launcher fallback resources.

Correct the root cause.

Do not fix this by adding another unrelated bitmap while leaving broken adaptive resources in place.

# 12. Adaptive icon requirements

The Beeline launcher icon must be a proper adaptive icon.

The bee must remain correctly positioned inside the Android adaptive-icon safe area.

It must not be:

- Cropped.
- Too large.
- Too small.
- Positioned differently across launcher masks.

Support common launcher masks such as:

- Circle.
- Rounded square.
- Squircle.
- Other adaptive masks selected by the launcher.

# 13. Theme-aware icon contrast

The icon must support appropriate light/dark contrast.

Required behavior:

- On a dark icon background, the bee should be light.
- On a light icon background, the bee should be dark.

Do not leave the bee permanently black.

Where Android themed/monochrome icons are supported, provide the correct adaptive/themed resource so the launcher can apply system icon theming correctly.

Where the launcher uses Beeline's normal adaptive icon instead of a themed icon, ensure that the foreground and background still have intentional contrast.

Use the project's existing theme/icon resources where appropriate.

Do not rely on the launcher dynamically recoloring an ordinary bitmap in a way Android does not guarantee.

# 14. Reference-image interpretation

Use the supplied screenshots as direct evidence of the remaining problems.

## Current setup screenshot

This shows that the main composition is broadly correct, but:

- Controls are too rectangular.
- The bee does not use the correct theme-aware foreground color.
- The intended pill geometry is missing.

Do not throw this implementation away.

Correct it.

## Current `welcome back` screenshot

This shows a more serious contrast issue:

- The background is dark.
- `welcome back` is black.
- `<3` is black.
- The result is difficult to read.

This must be fixed through theme-aware foreground roles rather than one-off color exceptions.

## Current share-sheet screenshot

This shows that:

- The action structure is close to the reference.
- The four upper bubbles exist.
- The three grouped lower controls exist.
- The primary remaining layout defect is excessive width.
- The lower labels are unnecessarily long.

Keep the structure and correct those problems.

## Installation screenshot

This proves that an icon asset exists and can be resolved by the package installer.

It does **not** prove that the launcher/adaptive icon configuration is correct.

Use it to narrow the investigation.

# 15. Do not introduce unrelated changes

Do not change:

- Setup navigation.
- Authentication behavior.
- Server validation.
- Keyboard logic.
- Account logic.
- Share action behavior.
- Post rendering.
- Global application colors.
- Global application typography.
- Other screens.

Do not turn this final correction into another redesign.

# 16. Validation

Test the finished implementation on a physical device if the development environment provides one.

For setup, verify:

- Initial setup still works.
- `i'm new, what's this?` still behaves exactly as before.
- `enter a server` remains directly editable.
- Server input behavior is unchanged.
- `next` still works.
- The bubble controls now have true pill geometry.
- The bee has correct contrast in light mode.
- The bee has correct contrast in dark mode.
- `welcome back` is readable in light mode.
- `welcome back` is readable in dark mode.
- `<3` is readable in both.
- Entered server text is readable in both.
- No setup-specific color change leaks into the rest of Beeline.

For the share sheet, verify:

- It is no more than two thirds of the available screen width.
- It reads as a floating card rather than a near-full-width sheet.
- Four upper bubbles remain.
- One lower grouped bubble remains.
- PM works.
- `Copy` copies the link.
- `Share` opens the Android share flow.
- Follow/unfollow works.
- Block works.
- Mute works.
- Report works.
- Layout remains usable in light and dark themes.

For the launcher icon, verify:

- Fresh install.
- Upgrade install where practical.
- Icon in Android's installer/package UI.
- Icon in the launcher/home screen.
- Icon in the app drawer.
- Round/adaptive launcher masks.
- Light-background icon presentation.
- Dark-background icon presentation.
- Themed icons where the test launcher supports them.

Do not consider the icon fixed only because Android Studio, the package installer, or application settings can display it.

The home-screen launcher icon must be verified directly.

# 17. Completion requirement

This should be the final correction pass for these three areas.

Before finishing, compare the running application directly against:

- `setupscreenBASIC.webp`
- `2sharesheet.jpg`
- The supplied defective screenshots.

Do not report completion while any of the specifically identified defects remain:

- Black bee on an incompatible dark/theme surface.
- Black unreadable setup text.
- Rounded rectangles where the reference requires pills.
- Near-full-width share sheet.
- `Copy link` instead of `Copy`.
- `Share with another app` instead of `Share`.
- Installer icon present but launcher icon missing or incorrect.
- Non-adaptive launcher foreground contrast.
