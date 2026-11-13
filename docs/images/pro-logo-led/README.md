# Pro Light Mode logo examples

These are unmodified captures from the running app on an Android 15 emulator.
The unlit logo and theme examples were captured with `LogoLedEvidenceTest` on
`7d63ad14`; the red examples are from `26d5665b`. The unlit logo uses the default
pad color, including when the theme supplies its own logo mask. The mode
comparison's before image is from `f59cb45c`; its after image was captured with
`LogoLedScreenTest` on `7d63ad14`, using the same 2400×1080 display. There are no
overlays or simulated app drawings.

The same emulator was resized for each layout, at density 320 (two pixels per
dp). These are layout checks, not physical tablet or Launchpad tests. The
fixture disables the watermark to isolate pack LEDs and restores the setting
afterward. It uses `o l a 5`, `o l a 45` and `f l` in `basefeatures-pack`.

| Layout | Unlit | Red logo LED |
| --- | --- | --- |
| Phone portrait, 720×1280, 360dp wide | ![Phone unlit](phone-portrait-default-off.png) | ![Phone red](phone-portrait-default-red.png) |
| Tablet portrait, 1600×2560, 800dp wide | ![Tablet portrait unlit](tablet-portrait-default-off.png) | ![Tablet portrait red](tablet-portrait-default-red.png) |
| Tablet landscape, 2560×1600, 1280dp wide | ![Tablet landscape unlit](tablet-landscape-default-off.png) | ![Tablet landscape red](tablet-landscape-default-red.png) |
| Optional diamond mask, tablet landscape | ![Theme unlit](theme-diamond-off.png) | ![Theme red](theme-diamond-red.png) |

The example mask has transparent corners and green source pixels. Its red
appearance demonstrates that the app uses alpha and applies the pack color.
The test creates a reusable `diamond-theme.zip` and `commands-pack.zip` in its
capture output; it also covers other colors, missing and damaged masks, and
bundled-theme fallback.

| Before | After: default logo, unlit |
| --- | --- |
| ![Before](before-pro.png) | ![After](after-pro.png) |


## Small packs and the menu

These captures use the same 1×5 pack on a 720×1280 display at density 320
(360dp wide). The before build is `19f2f40b` with only test identifiers
added; the after build is `c3ac4801`. Both use the default theme. The menu
background is included in the exclusion area, whether autoplay controls are
visible or hidden. The screen test also opens the menu to check it remains
usable. Images are copied directly from the device capture output.

| Controls | Before | After |
| --- | --- | --- |
| With autoplay | ![Before, autoplay visible](small-pack-before-autoplay-true.png) | ![After, autoplay visible](small-pack-after-autoplay-true.png) |
| Menu only | ![Before, menu only](small-pack-before-autoplay-false.png) | ![After, menu only](small-pack-after-autoplay-false.png) |
