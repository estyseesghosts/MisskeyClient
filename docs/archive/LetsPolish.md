# Task

Refine the existing Beeline interface so it matches the visual system in `DesignMockup.jpg`.

This is a visual implementation task.

The required features already exist.

Do not redesign their behavior.

Do not replace working logic.

Do not change protocol behavior.

Change only presentation, layout, sizing, shape, color use, and visual hierarchy where this task requires it.

Before you change code:

1. Read `importantdocs/writing_style.md`.
2. Follow that writing style for all notes and documentation.
3. Inspect the current implementation of each affected component.
4. Reuse existing components and state.
5. Inspect `docs/DesignMockup.jpg` directly.
6. Compare the running application with the mockup before you change anything.

The mockup defines the intended visual language.

Its exact padding values are not authoritative.

Its proportions, relationships, grouping, and shape logic are authoritative.

## Handle display rules

When the design says:

`@handle`

show only:

`@handle`

Do not expand it to:

`@handle@server`

unless the requirement explicitly asks for the full federated handle.

Keep this distinction consistent across all affected controls and labels.

Do not add the server name for extra context unless the design specifically requires it.
Do not truncate the full @handle@server from profile pages. 
The full @handle@server must always show on a user profile page. 
You can break up @handle and @server onto different lines to make it wrap correctly. 

# Core design rule

The mockup contains this explicit note:

> The padding & corner radii in this mockup may not be perfectly consistent. The colours and visuals are only for reference.

Do not copy inconsistent pixel measurements from the image.

Instead, create a consistent system.

The application must feel cohesive and polished.

The mockup also states:

> proportion and concentricity are key  
> so is consistency  
> this app must feel cohesive and polished

Treat this as a primary requirement.

# Text color rule

The mockup contains this explicit requirement:

> NEVER use white text or black text. Text must always use an accent colour from the theme.

Follow this rule for the components changed by this task.

Do not hard-code:

- White text.
- Black text.
- Mockup colors.

Use the active theme.

Text must use an appropriate theme-derived accent or foreground color.

The text must remain readable.

The result must work with:

- Light themes.
- Dark themes.
- Dynamic themes.
- User color overrides.

Do not invent new terms such as `branding colour`.

Use the theme roles that already exist in Beeline.

# Shared shape system

Bubble controls must use a common shape language.

Pay close attention to:

- Pill geometry.
- Corner radii.
- Nested shapes.
- Outer and inner radii.
- Equal spacing.
- Alignment.
- Concentric curves.

Do not put a small rounded rectangle inside an unrelated larger rounded rectangle.

The inner and outer shapes must visually belong together.

Where one rounded surface sits inside another, match their geometry.

# User-action card

The left side of the mockup shows the new visual form for the existing post/user action controls.

Do not change the existing actions.

Change their presentation.

The card must be compact.

It must not become a large generic bottom sheet.

# Default user-action state

The card starts with this heading:

`have a problem with @handle?`

Replace `@handle` with the applicable account handle.

Below the heading, show two half-width bubble controls.

The mockup shows:

- `follow @handle?`
- `message @handle?`

The note beside the mockup explicitly says:

> Note half-sized bubbles for follow, and new action (send message to OP of selected post)

The two actions must occupy one row.

Each action uses approximately half of the available width.

They must use matching bubble geometry.

The existing follow state remains contextual.

If the existing implementation shows `unfollow`, preserve that behavior.

The message action uses the existing direct-message behavior.

Do not create another messaging implementation.

# Share group inside the user-action card

Below the two account actions, show the existing share controls.

The mockup labels this section:

`share`

The card contains three controls.

The mockup describes this as:

> Card with three buttons  
> (similar to existing ui)

The three controls are:

- Direct-message control.
- Link-copy control.
- Android share control.

These controls remain inside one grouped rounded surface.

Do not make three unrelated cards.

Do not change their existing behavior.

# Problem actions

The heading:

`have a problem with @handle?`

must be interactive.

The mockup states:

> Show this version on tap of the 'problem' text

When the user selects that text, change the card presentation.

Show four half-width bubble controls in a two-column layout.

The mockup shows:

- `block @handle?`
- `mute @handle?`
- `report @handle?`
- `no, it's okay`

Use the existing block, mute, and report behavior.

`no, it's okay` returns to the normal card state.

Do not change the underlying moderation actions.

Do not make this a separate screen.

This is a compact state change inside the existing action card.

# User-action card proportions

The card must remain small enough to feel contextual.

Do not make each control full screen width.

The half-width controls must be visually balanced.

The two-column rows must use consistent widths.

The card must use the same bubble vocabulary as the rest of Beeline.

The moderation version and normal version must have matching outer dimensions where practical.

Avoid noticeable card jumps when the state changes.

# Navigation bar

The center of the mockup defines the required navigation geometry.

The mockup contains this explicit requirement:

> Navigation bar should not be oversized  
> should perfectly contain four bubbles  
> the leftmost and rightmost bubbles  
> should be concentric, lined up with  
> the edges and corner radius of  
> the pill behind the bubble buttons.

Follow this carefully.

# Navigation background

The normal navigation items sit inside one horizontal pill.

The pill must fit its contents closely.

Do not make the navigation background significantly wider or taller than necessary.

Do not use excessive padding.

The navigation bar must feel compact.

# Four contained navigation bubbles

The mockup shows four contained navigation items:

- `H`
- `S`
- `N`
- `P`

These letters only illustrate position.

Use the application's real navigation icons.

The four normal navigation controls must sit inside the background pill.

Their individual bubble shapes must align with the containing pill.

The first bubble must follow the left curve of the outer pill.

The last contained bubble must follow the right curve.

Their curves must appear concentric with the outer surface.

# Contextual navigation action

The mockup also shows a separate contextual bubble marked:

`C`

This sits directly beside the main four-item navigation pill.

Use the existing contextual navigation action in this position where the current interface provides one.

Do not invent a new feature because the mockup uses the letter `C`.

The mockup uses `C` only to demonstrate the contextual control.

# Navigation color hierarchy

The mockup states:

> note how selected icon is accented  
> contextual is accented  
> while the not-in-use tab indicators  
> are greyed/desaturated

Implement this hierarchy.

The selected navigation control uses the active theme accent.

The contextual control also uses an accent treatment.

Inactive navigation controls use a quieter or desaturated treatment.

Do not give every navigation control equal visual emphasis.

Do not use hard-coded gray values if the theme already provides suitable colors.

# Navigation geometry

The mockup explicitly states:

> pay careful attention  
> to concentric shapes  
> and matching  
> corner radii

Treat this as a strict visual requirement.

Inspect the result at normal device scale.

Do not judge the geometry only from Compose Preview.

The navigation pill and internal controls must appear intentionally constructed as one component.

# Hashtag list

The right side of the mockup defines the hashtag-list presentation.

The existing hashtag behavior remains unchanged.

This task changes only its visual presentation and compact/expanded layout.

# Compact list first

The mockup explicitly states:

> show compact list first

Always show the compact version first.

Do not immediately show the complete hashtag list.

# Compact hashtag count

The mockup states:

> only show 3-6 hashtags in compact list

Select the visible count dynamically.

The mockup requires the count to depend on:

- Device screen size.
- Font display size.
- Available screen space.

Do not hard-code one count for every device.

The compact view must show no fewer than three hashtags when space permits.

It must show no more than six.

# Compact hashtag layout

The compact mockup shows one narrow rounded container.

At the top is:

`see all?`

Below it are hashtag bubbles.

The example contains:

- `#cooking`
- `#photography`
- `#anime`
- `#国内旅行`

These hashtags are examples only.

Use the actual hashtags supplied by the current feature.

Each hashtag is its own pill.

The container and hashtag pills must use matching shape geometry.

The compact list must remain visually small.

# Expanded hashtag layout

Selecting:

`see all?`

opens the expanded version.

Do not change the underlying hashtag data.

The expanded mockup shows one larger rounded container.

Hashtags appear in two columns where available space permits.

The mockup examples include:

- `#cooking`
- `#transit`
- `#photography`
- `#linux`
- `#anime`
- `#anime`
- `#国内旅行`
- `#lemmy`
- `#cooking`
- `#canpoli`
- `#photography`
- `#music`
- `#anime`
- `#fedi`
- `#国内旅行`
- `#hashtag`

These are layout examples.

Do not hard-code these values.

Use the real hashtag list.

# Expanded hashtag close control

The bottom of the expanded container contains:

`close`

This returns to the compact list.

Do not add another modal navigation system.

The expanded and compact states belong to the same existing hashtag component.

# Hashtag text sizing

The mockup intentionally shows different text lengths and scripts.

The layout must handle:

- Short hashtags.
- Long hashtags.
- Large system font sizes.
- Latin text.
- CJK text.
- Other scripts.
- Narrow screens.

Do not require all hashtag pills to have identical widths.

Do not clip text unnecessarily.

Do not allow one large hashtag to destroy the complete layout.

# Adaptive layout

All affected components must adapt to available space.

Test:

- Compact phones.
- Large phones.
- Foldables.
- Tablet layouts.
- Large system font settings.

The visual system must preserve its proportions.

Do not solve narrow-screen problems by removing required controls.

# Existing behavior must remain unchanged

This task is a presentation pass.

Preserve all existing functional behavior.

Do not change:

- Follow logic.
- Unfollow logic.
- Direct messages.
- Block behavior.
- Mute behavior.
- Report behavior.
- Copy behavior.
- Android share behavior.
- Navigation destinations.
- Selected navigation state.
- Contextual action behavior.
- Hashtag selection.
- Hashtag data.
- Hashtag loading.
- Feed behavior.
- Protocol behavior.
- Account state.

If you must modify state to support a visual transition, keep the functional result identical.

## Emoji favorite pin confirmation

Change the current long-press pin interaction for emoji favorites.

When the user long-presses an emoji to pin it to favorites, show a compact bubble confirmation.

Use the same visual pattern as the existing repost confirmation control.

The bubble text must be:

`pin emoji?`

Do not use the current long message bar.

The current design has these problems:

- The message is too long.
- The actual action target is too small.
- The control can be cut off on phone screens.
- The design does not match the rest of Beeline.

The new confirmation must:

- Use the same bubble style as the repost confirmation.
- Use a large enough touch target.
- Fit on compact phone screens.
- Remain theme-aware.
- Dismiss cleanly if the user does not confirm.
- Keep the existing pin-to-favorites behavior unchanged.

This is a visual and interaction-surface change only.

Do not change the underlying emoji favorite logic.

# Reuse existing code

Do not build parallel versions of these features.

Reuse:

- Existing action handlers.
- Existing navigation state.
- Existing hashtag data.
- Existing theme values.
- Existing bubble components where suitable.

Extract common visual primitives if this improves consistency.

Examples can include:

- A shared bubble shape.
- Shared bubble padding.
- Shared nested-surface spacing.
- Shared selected and inactive treatments.

Do not introduce a large new design framework for three components.

# Consistency audit

After you update these components, inspect nearby existing bubble controls.

Look for obvious visual inconsistencies.

Do not redesign unrelated screens.

You can align shared tokens where that safely improves consistency.

The result must look like one application.

It must not look like three separately designed components.

# Visual validation

Automated tests alone are not sufficient for this task.

Build and run the application.

Inspect the real interface.

Compare it directly with `DesignMockup.jpg`.

Validate:

- User-action card size.
- Half-width account actions.
- Two-column moderation actions.
- Three-control share group.
- Compact navigation height.
- Compact navigation width.
- Concentric navigation shapes.
- Selected navigation accent.
- Contextual control accent.
- Inactive navigation desaturation.
- Compact hashtag view.
- Dynamic 3-to-6 hashtag limit.
- Expanded hashtag view.
- Two-column hashtag layout where suitable.
- `see all?`.
- `close`.
- Theme-aware text.
- Light theme.
- Dark theme.
- Large text settings.
- Compact screen.
- Large screen.

# Geometry validation

Inspect nested rounded surfaces at high visual scale.

Check:

- Left edges.
- Right edges.
- Top and bottom spacing.
- Corner radii.
- Pill height.
- Inner-to-outer spacing.
- Symmetry.
- Concentricity.

A component can be functionally correct and still fail this task if its geometry looks careless.

Fix visible one-pixel-style alignment errors where Compose density calculations make them apparent.

Do not chase literal mockup pixels when the mockup itself is inconsistent.

Use a coherent geometric system.

# Completion standard

Do not mark the task complete because the code compiles.

Do not mark it complete because the components are approximately similar.

The task is complete only when:

- All existing functionality remains intact.
- The interface follows the mockup's hierarchy.
- Bubble shapes match each other.
- Nested shapes are concentric.
- Navigation is compact.
- Navigation emphasis is correct.
- User-action controls have the required grouping.
- Moderation controls use the required two-column state.
- Hashtags start compact.
- Compact hashtags adapt between three and six items.
- Expanded hashtags remain readable.
- Theme-derived text colors replace hard-coded white or black text.
- Light and dark themes both look intentional.
- The application feels visually cohesive.

The final result must look deliberately designed.

Do not accept "close enough" geometry when the inconsistency is visible.
