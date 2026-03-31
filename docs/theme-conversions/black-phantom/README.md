# Black Phantom Theme Conversion

This folder contains a ZIP theme converted from the legacy APK theme `BlackPhantom.apk` for the current UniPad ZIP theme format.

## Files

- `BlackPhantom_unipad_theme.zip`: Ready-to-import ZIP theme for current UniPad builds.

## Source Metadata

- Original APK package: `com.kimjisub.launchpad.theme.clementphantomv2`
- Original theme name: `Black Phantom`
- Original author: `Clement Show`
- Original version: `1.0.0`

## Included Assets

The ZIP includes all legacy APK assets that are supported by the current ZIP theme loader:

- `theme.json`
- `theme_ic.png`
- `playbg.png`
- `btn.png`
- `btn_.png`
- `phantom.png`
- `phantom_.png`
- `chain.png`
- `chain_.png`
- `chain__.png`

## Notes

- The original APK used drawable-based chain assets, so this conversion keeps `chain.png`, `chain_.png`, and `chain__.png` instead of `chainled.png`.
- Legacy APK-only assets such as `play.png`, `prev.png`, `next.png`, `pause.png`, and `appicon.png` are not included in the final ZIP because the current UniPad ZIP theme format does not load them.
- Visual parity is limited to the assets supported by the new ZIP theme system.

## Import

1. Open UniPad.
2. Go to `Theme -> Add Theme -> Import ZIP file`.
3. Select `BlackPhantom_unipad_theme.zip`.
