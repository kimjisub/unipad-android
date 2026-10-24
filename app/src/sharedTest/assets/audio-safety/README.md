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
  trimming; only the final packet may be partial. A first AAC packet with zero timestamp
  delta still reserves one full codec block. ALAC rate/channels come from ALACSpecificConfig,
  including rates above the 16.16 sample-entry field range. Ogg validates packet block lengths and
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
WAV ignores stale RIFF sizes and counts physically present complete PCM blocks in `data`
(or complete MPEG frames for WAVE_FORMAT_MPEGLAYER3);
an incomplete trailing non-audio chunk or missing final pad does not discard audio.
The legacy `invalid-data-tone.wav` name is retained for byte/hash continuity: its oversized
`data` claim now yields 13,230 physical frames rather than rejecting the complete payload.
MP3 skips leading zero padding interleaved with ID3 tags and counts complete frames only,
ignoring a partial final frame and trailing non-audio bytes/tags. It resynchronizes after
inter-frame non-audio bytes so a gap cannot hide later complete frames. A truncated MP3 therefore loses one full 1,152-frame
reservation, while a truncated mono 16-bit WAV loses one frame. Other truncated containers
still fail their structural/checksum checks.

Compatibility mutations of the original tones are `riff-small.wav` (RIFF size 36),
`riff-large.wav` (RIFF size file length + 1000), `trailing-junk.wav` (incomplete JUNK chunk),
`data-longer-than-file.wav` (data size + 1000), `riff-excludes-pad.wav` and `missing-pad.wav`
(final one-byte JUNK payload, respectively with an uncounted pad and no pad),
`leading-zero.mp3` (137 zero bytes), `trailing-bytes.mp3` (`not audio`) and
`trailing-ape.mp3` (an empty APEv2 footer). `middle-zero.mp3` inserts one zero byte
between the first 208-byte MPEG frame and the remaining frames, preserving 16,128 frames;
`leading-zero-id3.mp3` prepends one zero to the existing ID3 fixture.
`leading-false-header.mp3` prefixes an unrelated MPEG-2 header and 1,280 zero bytes;
the consecutive MPEG-1 frames establish the actual layout. `false-sync.mp3` and
`false-layer-sync.mp3` insert a header claiming to cover subsequent consistent frames;
this ambiguous damage is rejected rather than admitted with a smaller reservation.
Some decoders recover such damaged streams; acceptance here does not promise decoder
agreement. `binary-ape.mp3`, `binary-ape-with-header.mp3` and `binary-ape-id3v1.mp3`
contain header-shaped binary metadata. Validate every physical APE item (key, flags and
value range), optional header and footer before excluding the tag from the audio span.
An advertised footer size alone must not hide audio. `mp3-in-wave.wav`
was generated with `ffmpeg -i tone.wav -c:a libmp3lame -b:a 128k mp3-in-wave.wav`;
ffprobe independently enumerates thirteen 1,152-frame MPEG packets (14,976 frames). `zero-first-packet.m4a` splits the initial
13-packet run into one zero-delta packet and 12 unchanged packets; all physical AAC packets
are preserved. `zero-middle-packets.m4a` additionally zeroes that second run and is invalid.
`alac-96000.m4a` was generated with `ffmpeg -f lavfi -i sine=r=96000:d=2 -ac 2 -c:a alac`;
its ALAC configuration independently specifies 96,000 Hz, two channels and 192,000 frames.

Inspection work ceilings are separate from the integer sound/pack limits. MPEG header
searches within each MPEG payload share at most `min(1048576, max(4096, 4 * physicalPayloadBytes))` probes; exhaustion
is READ_FAILURE, never a partial low-cost success. The resource test adds 1,024 unrelated
five-byte header patterns to a tone and verifies this bounded failure.

MP4 inspection ceilings protect the reader separately from the integer sound/pack limits:
4,096 sibling boxes, 262,144 rows per timing/chunk/mapping table and 1,048,576 variable
sample sizes. Exceeding a ceiling is READ_FAILURE, not INVALID_FORMAT or a sound-cost
exclusion. Timing/chunk/mapping table extents must physically exist before a capacity failure is reported.
Chunk overlap checks use two bounded primitive arrays (at most 4 MiB total), and physical
mdat lookup uses binary search. The resource tests generate a PCM MP4 with 4,096 or 262,145
one-sample chunks and a file with 4,097 extra empty boxes; oversized inputs must fail before
allocating per-chunk objects. The reader does not decode samples or allocate claimed PCM.
These are structural admission tests, not a decoder or a promise of equal audible duration.
Decoder delay, padding, edit lists, codec errors, peak process memory and device failures
must be checked separately when this plan is connected to playback.

Format references: [FLAC framing and precision](https://www.rfc-editor.org/rfc/rfc9639.html#section-9.1.4),
[Opus packet framing](https://www.rfc-editor.org/rfc/rfc6716.html#appendix-B),
[Ogg framing](https://xiph.org/ogg/doc/framing.html),
[Vorbis mapping](https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html),
[MP4 time-to-sample tables](https://developer.apple.com/documentation/quicktime-file-format/time-to-sample_atom).
