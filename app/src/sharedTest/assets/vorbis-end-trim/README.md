# Vorbis end trim

`short-tail.ogg` is byte for byte the `e2e/fixtures/vorbis-short-tail.ogg` of
[unipad.io](https://github.com/kimjisub/unipad.io), where its generator
`e2e/fixtures/generate-vorbis-short-tail.mjs` lives (SHA-256
`b6dbd32764dcb2ada6ddffa4bef76cfc882be5422933673759d4d882e65d9455`).

It repeats one short packet of a 440 Hz, 44.1 kHz mono sine encoded with libvorbis (`oggenc` 1.4.3).
The identification header specifies 256/2048-frame blocks and all 141 audio packets use the short
mode, so their overlap contributes 17,920 frames. The last page ends the stream at 17,640, trimming
280 frames: more than the final short block, within one long block. CRCs, sequence numbers and page
lengths are valid. No third-party recording is included.

Real encoder output ends this way too: libvorbis can finish a stream with two short blocks and
trim more than 256 frames from them, and `oggdec` and FFmpeg honour that trim. The inspection
therefore lets a Vorbis stream trim up to one long block from its end; Opus still trims only within
its final packet. The unit tests change only the final granule and its page CRC to cover the
inclusive long-block bound and rejection beyond it; the device test checks what the platform
decoder actually emits for the file.
