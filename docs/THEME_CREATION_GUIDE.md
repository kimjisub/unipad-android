# UniPad Theme Creation Guide

This guide explains how to create ZIP themes that customize the appearance of the UniPad app.

## File Structure

```
my-theme.zip
├── theme.json          (Required) Theme metadata
├── theme_ic.png        (Required) Theme icon/thumbnail
├── playbg.png          Background image
├── btn.png             Pad button (default state)
├── btn_.png            Pad button (pressed state)
├── phantom.png         Guide overlay
├── phantom_.png        Guide overlay variant (optional)
├── custom_logo.png     Custom logo (optional)
├── chainled.png        Chain LED mode image (Chain Mode A)
├── chain.png           Chain default state (Chain Mode B)
├── chain_.png          Chain selected state (Chain Mode B)
├── chain__.png         Chain guide state (Chain Mode B)
└── colors.json         UI color customization (optional)
```

## Required Files

### theme.json

A JSON file containing the theme's name, author, and version information.

```json
{
  "name": "My Theme",
  "author": "Your Name",
  "version": "1.0.0"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Theme display name |
| `author` | string | Yes | Creator name |
| `version` | string | No | Version (default: `"1.0"`) |

### theme_ic.png

The icon displayed in the theme selection list. Rendered at 44dp with rounded corners.

- Recommended size: 132x132px (xxhdpi)
- Square images recommended

## Image Resources

All image files must be in **PNG format**. Any missing resources will fall back to the app's default theme.

### Pad Area

| File | Description | Usage |
|------|-------------|-------|
| `playbg.png` | Full background of the play screen | Displayed behind the pad grid |
| `btn.png` | Pad button default state | Each pad in the 8x8 grid |
| `btn_.png` | Pad button pressed state | When a pad is touched/pressed |
| `phantom.png` | Guide overlay | Used for autoplay guides, etc. |
| `phantom_.png` | Guide overlay variant | Alternates with phantom.png in a 2x2 pattern on even-sized pads (optional) |
| `custom_logo.png` | Custom logo | Displayed as an overlay on the play screen (optional) |

### Chain Area

Choose one of two chain modes:

**Mode A: LED Mode** (when `chainled.png` exists)

| File | Description |
|------|-------------|
| `chainled.png` | Chain LED image. LED colors are applied as overlays |

In this mode, chain buttons use the pad button (`btn.png`) as background, with `chainled.png` placed on the phantom layer and LED colors overlaid on top.

**Mode B: Drawable Mode** (when `chainled.png` does not exist)

| File | Description |
|------|-------------|
| `chain.png` | Chain default state / LED channel |
| `chain_.png` | Chain selected state |
| `chain__.png` | Autoplay guide state |

In this mode, different images are displayed directly for each state.

## Color Customization (Optional)

### colors.json

You can customize the colors of UI elements. All fields are optional; unspecified fields will use default colors.

```json
{
  "checkbox": "#FF5722",
  "trace_log": "#2196F3",
  "option_window": "#424242",
  "option_window_checkbox": "#FF9800"
}
```

| Field | Description |
|-------|-------------|
| `checkbox` | Option checkbox color |
| `trace_log` | Trace log text color |
| `option_window` | Option panel background color |
| `option_window_checkbox` | Checkbox color inside the option panel |

Color values should be hex strings in `#RRGGBB` or `#AARRGGBB` format.

## Screen Layout Reference

```
┌──────────────────────────────────┐
│           playbg.png             │
│  ┌─────────────────────┐ ┌───┐  │
│  │  8x8 Pad Grid       │ │ C │  │
│  │  ┌───┬───┬───┬───┐  │ │ h │  │
│  │  │btn│btn│btn│btn│  │ │ a │  │
│  │  ├───┼───┼───┼───┤  │ │ i │  │
│  │  │btn│btn│btn│btn│  │ │ n │  │
│  │  ├───┼───┼───┼───┤  │ │   │  │
│  │  │btn│btn│btn│btn│  │ │   │  │
│  │  ├───┼───┼───┼───┤  │ │   │  │
│  │  │btn│btn│btn│btn│  │ │   │  │
│  │  └───┴───┴───┴───┘  │ └───┘  │
│  │   phantom overlay    │        │
│  └─────────────────────┘        │
│         custom_logo.png         │
└──────────────────────────────────┘
```

## Examples

### Minimal Setup (Required Files Only)

```
minimal-theme.zip
├── theme.json
└── theme_ic.png
```

All resources will fall back to the app's default theme. Only the icon and name will appear in the theme list.

### Full Setup

```
full-theme.zip
├── theme.json
├── theme_ic.png
├── colors.json
├── playbg.png
├── btn.png
├── btn_.png
├── phantom.png
├── phantom_.png
├── custom_logo.png
└── chainled.png
```

### theme.json Example

```json
{
  "name": "Neon Glow",
  "author": "UniPad Community",
  "version": "2.1.0"
}
```

### colors.json Example

```json
{
  "checkbox": "#00E5FF",
  "trace_log": "#76FF03",
  "option_window": "#1A1A2E",
  "option_window_checkbox": "#E94560"
}
```

## Installation

1. Prepare your files according to the structure above.
2. Compress all files into a single ZIP file.
   - Files must be located at the ZIP root (do not place them inside a subfolder).
3. In the UniPad app, go to Theme → Add Theme → **Import ZIP file**.
4. Select the ZIP file and it will be automatically validated and installed.

## Validation Rules

The following items are validated during import:

- `theme.json` must exist at the ZIP root.
- `theme_ic.png` must exist at the ZIP root.
- `theme.json` must be valid JSON with `name` and `author` fields required.

If validation fails, the theme will not be installed and an error message will be displayed.

## Tips

- Using transparent backgrounds (alpha channel) in images allows LED colors to blend naturally.
- `btn.png` has LED colors overlaid on top, so dark-toned images work best.
- `phantom_.png` alternates with `phantom.png` in a 2x2 pattern when the pad size is even (e.g., 8x8). Use a slightly different design for visual distinction.
- Chain Mode A (LED mode) dynamically applies LED colors to a single image, allowing diverse states with just one image.
- Chain Mode B (Drawable mode) gives you direct control over each state's image, enabling more creative freedom in design.

## Legacy APK Conversion Example

An example conversion from a legacy APK theme to the current ZIP theme format is available here:

- [Black Phantom conversion](theme-conversions/black-phantom/README.md)
