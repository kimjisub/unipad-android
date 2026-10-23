# Audio admission corpus

`cases.json` is the language-neutral contract. Integers are decimal and must not pass through
floating-point duration arithmetic. Values beyond JavaScript's exact integer range are quoted decimal
strings; parse them as integers, not as floating-point numbers. `frames` counts frames per channel, not interleaved samples.
The cost is `frames * max(channels, 2)` at 48 kHz or above; below that rate, first round
`frames * 48000 / rate` upwards. Inclusive limits are 600 seconds, 67,108,864 per sound,
and 134,217,728 per pack. Rates must be 8,000–384,000 Hz and channels 1–8.

- `costs` supplies independent input/expected pairs, including overflow-sized inputs.
- `files` supplies binary inputs and exact structural frame counts or rejection reasons.
  The original tones are the app's existing, generated 0.3 s tone fixtures. Names do not
  select parsers. MP3 counts include the first Info/Xing frame and encoder padding;
  M4A validates full AAC packet durations and counts the media timeline before edit-list
  trimming; only the final packet may be partial. Ogg validates packet block lengths and
  removes a positive initial granule origin. A one-page positive-origin Vorbis stream has
  no separately measurable end trim, so its complete packet-derived frame count is reserved.
- `wavRecipes` describes real, zero-valued PCM files without committing hundreds of MB.
  Write RIFF/WAVE, a 16-byte PCM `fmt ` chunk (16 bits, rate/channels as given), a four-byte
  `fact` chunk containing `claimedSeconds * rate`, and a `data` chunk containing exactly
  `frames * channels * 2` zero bytes. Set RIFF size to file size minus eight. Sparse zero
  extents are equivalent to explicitly written zero bytes. Ignore `fact` for PCM length.
- `selections` lists zero-based `[chain, x, y, keySoundLine, resolvedFileIdentity]` references.
  Exclude out-of-bounds entries before deduplication, sort numerically by the first four
  fields, and inspect each resolved identity once. `facts` triples are `[rate, channels,
  frames]`; null means a read failure. `order`, `selected`, `exclusions`, and `reservedCost`
  are fixed expected results. Do not release reservations when decoding subsequently fails.

Each binary input has a SHA-256 in the manifest. Additional generated tones cover raw ADTS AAC,
FLAC, Opus mono and Opus 5.1. The constant 32-bit FLAC uses the valid frame precision code 7,
including real subframes and checksums; FFmpeg independently emits its 192 zero-valued frames. The leading-padding and backwards-chunk M4A fixtures retain the
same AAC packets. Invalid packet timing, missing AAC configuration, missing FLAC subframes and
incomplete Opus length fields must be rejected even when container checksums are valid.

The original Vorbis fixture has a final granule of 13,248 frames (also reported by ffprobe),
not the 13,230 frames suggested by its rounded 0.3 s description. FFmpeg emits 13,312 frames.
The positive-origin variant independently emits 13,312 frames too. These differences are
recorded rather than treated as established decoder agreement.

The claimed-duration fixtures alter metadata only. Truncated fixtures remove the last byte.
These are structural admission tests, not a decoder or a promise of equal audible duration.
Decoder delay, padding, edit lists, codec errors, peak process memory and device failures
must be checked separately when this plan is connected to playback.

Format references: [FLAC framing and precision](https://www.rfc-editor.org/rfc/rfc9639.html#section-9.1.4),
[Opus packet framing](https://www.rfc-editor.org/rfc/rfc6716.html#appendix-B),
[Ogg framing](https://xiph.org/ogg/doc/framing.html),
[Vorbis mapping](https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html),
[MP4 time-to-sample tables](https://developer.apple.com/documentation/quicktime-file-format/time-to-sample_atom).
