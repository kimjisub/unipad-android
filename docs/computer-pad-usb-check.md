# Computer MIDI pad: checking a real phone over USB

The computer MIDI pad (Settings → Device → Computer MIDI pad) makes the phone a USB MIDI device for
a computer: pads send presses to the computer and the computer sends lights back. The device tests
(`UsbPeripheralPortsTest`, `ComputerPadScreenTest`, `ComputerPadPortLoadTest`) open real Android
MIDI ports, but against a test service on the same phone or emulator. They never involve a cable
or a computer. This page is the check that does, written so that every run records the same values
in the same order.

A result without both ends recorded (the computer's byte log and the phone's screen recording) is
**not checked**, not passed. A failure is kept as it was found: record it, keep its files, and do
not replace it with a later passing run.

## What to prepare

- A phone and a USB data cable (a charge-only cable shows no MIDI at all).
- A build of UniPad that shows **Computer MIDI pad** in Settings. The entry is currently shown in
  debug builds only (`COMPUTER_PAD_ENTRY` in `app/build.gradle`).
- A Mac for the computer side. `scripts/computer_pad_midi.swift` uses CoreMIDI only and runs with
  the `swift` that comes with Xcode or the Command Line Tools; nothing else is installed.
- A way to record the phone's screen: the phone's own screen recorder, or `adb shell screenrecord`
  over wireless debugging. While the cable is in MIDI mode, adb over USB is not available.
- The phone writes no byte log. Its end of the record is the screen recording, so keep the phone's
  status bar clock visible, and note the Mac's time when the recording starts.

Before connecting the phone, try the tool without a phone: run `fake-phone` in one terminal, then
`list`, `log`, `colors` and `flood` against "UniPad check fake phone" in another. Everything sent to
the fake phone comes back to its source.

```sh
swift scripts/computer_pad_midi.swift fake-phone 60
swift scripts/computer_pad_midi.swift list
```

Compiling first makes each command start at once: `swiftc -O -o computer_pad_midi scripts/computer_pad_midi.swift`,
then run `./computer_pad_midi list` and so on.

## Values to record for every run

| Value | Example of what to write |
|---|---|
| Who ran it, date and time zone | |
| Phone model and model number | as in Settings → About phone |
| Android version and build number | as in Settings → About phone |
| Cable | type at each end (USB-C to USB-C, USB-C to A), brand, data or charge-only, any hub or adapter |
| Phone USB setting chosen | the exact menu path and the name of the MIDI choice on this phone |
| Computer model and OS version | as in About This Mac / System Information |
| UniPad version | version name and code (Android Settings → Apps → UniPad) and whether debug or release |
| Device name on the computer | from `list`: device, source and destination names |
| Port numbers | the `list` numbers of the source (presses) and destination (lights) used |
| Ports chosen on the phone | the device, press port and light port names the phone shows |
| Files | `list` output, every `.tsv` log, the phone screen recording, photos |

## Steps

Each step has expected bytes. Hex is MIDI 1.0 on channel 1; the pad number layout is the one in the
app's help: pads 11–88 (81 top left, 18 bottom right), right buttons 89 (top) to 19, top buttons
CC 104–111. A press is `90 <number> 7F`, its release `80 <number> 00`; a top button is
`B0 <104–111> 7F`, released with value `00`.

### 1. The computer sees the phone

1. Run `swift scripts/computer_pad_midi.swift list` with the phone unplugged. Save the output.
2. Plug in the cable and choose MIDI in the phone's USB settings. Record the menu path and choice.
   If the phone has no MIDI choice, record that; this phone cannot use the computer pad.
3. Run `list` again and save it. Record the new device, source and destination names and numbers.

### 2. The phone opens the ports

1. Open Settings → Device → Computer MIDI pad. Expected: "USB MIDI found".
2. Record the device and port names shown, choose them, and press **Connect**.
   Expected: "Port open · whether the computer receives it is checked separately". This only says
   the phone opened its ports; the next step checks that the computer receives.

### 3. Presses reach the computer

1. On the Mac: `swift scripts/computer_pad_midi.swift log <source> > presses.tsv`.
2. Start the phone's screen recording.
3. Press, one at a time: the top left pad, the bottom right pad, the first top button, the first
   right button; then two fingers on the top left pad and its right neighbour; then one finger
   dragged from the top left pad across two pads to the right and lifted.
4. Stop the log with Ctrl-C.

Expected in `presses.tsv`, in this order:

| Action | Bytes |
|---|---|
| Top left pad | `90 51 7F`, `80 51 00` |
| Bottom right pad | `90 12 7F`, `80 12 00` |
| First top button | `B0 68 7F`, `B0 68 00` |
| First right button | `90 59 7F`, `80 59 00` |
| Two fingers on 81 and 82 | `90 51 7F`, `90 52 7F`, then each `80 .. 00` as its finger lifts |
| Drag 81 → 82 → 83 | `90 51 7F`, `80 51 00`, `90 52 7F`, `80 52 00`, `90 53 7F`, `80 53 00` |

Every press has exactly one release; nothing else appears.

### 4. Lights reach the phone

1. Keep a `log` running in a second terminal (`> lights-echo.tsv`): the phone must send nothing back
   while it receives lights.
2. With the screen recording running:
   `swift scripts/computer_pad_midi.swift colors <destination> 100 > lights.tsv`.
3. Expected on the phone: all 80 positions show color 0, 1, …, 127 in turn (0 is off), then 5,
   then off (note off at differing velocities, top buttons by CC value 0), then 9, then off
   (note on with velocity 0 and CC value 0). Colors are the app's palette and need not match a
   hardware Launchpad.
4. Expected in `lights-echo.tsv`: no messages.

### 5. Many lights at once

1. With `log` and the screen recording running:
   `swift scripts/computer_pad_midi.swift flood <destination> 40000 > flood.tsv`.
   The tool reports how long sending took; only its last 80 messages are written to `flood.tsv`.
2. Expected: the phone's screen keeps responding (pads can be pressed during and right after), each
   position ends with the color in the last 80 lines of `flood.tsv`, and the app does not close.
3. Right after, press the top left pad once. Expected in the log: `90 51 7F`, `80 51 00`.

### 6. Disconnecting

Record each case on its own row, with the log running.

| Case | Expected |
|---|---|
| **Disconnect** while holding a pad | the held pad's release arrives, then nothing |
| Disconnect and **Connect** ten times, one press and release and one light each time | each round shows only that round's press, release and light; no earlier light stays on the phone; the app does not close |
| Unplug the cable while holding a pad | the phone shows that the connection ended and clears its presses and lights; whether the computer received a release is **not guaranteed**: record what the log shows and stop any sound left on the computer |
| Switch to another app, or lock the phone, while holding a pad | the release arrives; coming back waits for **Connect again** |
| Close UniPad from the recent apps while holding a pad | record what the log shows; a release is not guaranteed |

### 7. Latency

There are three separate sections. Report each on its own; never combine them, and never report
half of a round trip as one-way time.

1. **Inside the phone, touch received → MIDI send call.** This starts when the screen's window
   receives the touch; the touch screen and the system's input dispatch before it are left out.
   Run the device test on the phone over adb
   (before switching the cable to MIDI, or over wireless debugging):

   ```sh
   ANDROID_SERIAL=<phone> bash scripts/ci-connected-tests.sh \
     com.kimjisub.launchpad.midi.ComputerPadPortLoadTest#touchToSendCallTimesInsideThePhone
   ```

   It sends 10,000 press–release pairs at 100 pairs a second (about two minutes) and writes the
   median, 95th and 99th percentile, maximum, missing and out-of-order counts to
   `computer_pad/touch_to_send.txt` among the captures. `-e computerPadLatencyPairs <n>` changes the
   number of pairs. Only missing or reordered messages fail the test. Figures from an emulator are
   that emulator's own and are not phone figures.
2. **Computer → USB → phone light → computer.** Needs a test-only reply in a test build; the app
   has none. Record **not measured**.
3. **Real touch → computer sound, computer light → phone screen.** Needs high-speed video or
   synchronised measurement, and includes the screen's refresh and the music program's buffer.
   Record **not measured** unless that equipment was used, and describe it if it was.

## Windows (not yet checked)

These steps have not been run on Windows. Treat every Windows result as unverified until a run
records the values above.

1. Plug in the phone and choose MIDI in its USB settings.
2. Device Manager should list a new audio/MIDI device; record its name and the driver shown.
3. Windows has no built-in MIDI monitor. Use a MIDI monitor program and record its name and version;
   repeat steps 3 to 6 with it, saving its log with times.
4. Sending 80 positions × 128 colors needs a program that can send MIDI; record which one and how.

## Result table

Copy this table for each run. Result is **pass**, **fail** or **not checked**, and every pass names
its evidence files.

| Step | Expected | Observed | Evidence (file, time) | Result |
|---|---|---|---|---|
| 1. Phone listed on the computer | new source and destination | | | |
| 2. Ports open on the phone | "Port open · …" | | | |
| 3. Presses reach the computer | bytes as in step 3 | | | |
| 4. Lights, 80 × 128 and off | all positions, all colors; nothing sent back | | | |
| 5. Many lights | screen responds, last colors stay, press right after arrives | | | |
| 6. Disconnect while holding | release arrives | | | |
| 6. Ten reconnections | nothing carried over, app stays open | | | |
| 6. Cable unplugged while holding | recorded as found | | | |
| 6. Other app / lock while holding | release arrives | | | |
| 6. App closed while holding | recorded as found | | | |
| 7.1 Inside the phone | figures, missing 0, out of order 0 | | | |
| 7.2 Round trip | not measured | | | |
| 7.3 Touch to sound, light to screen | not measured | | | |
