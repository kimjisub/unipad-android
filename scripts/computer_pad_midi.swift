// Computer side of the computer pad check on macOS, with CoreMIDI only: no installs.
//
//   swift scripts/computer_pad_midi.swift list
//   swift scripts/computer_pad_midi.swift log <source> [seconds]
//   swift scripts/computer_pad_midi.swift colors <destination> [milliseconds between colors]
//   swift scripts/computer_pad_midi.swift flood <destination> [messages]
//   swift scripts/computer_pad_midi.swift fake-phone [seconds]
//
// <source> and <destination> are the numbers `list` prints. `log`, `colors` and `flood` print one
// tab-separated line per MIDI message: wall clock (UTC), host clock in nanoseconds (monotonic, the
// same clock in every command on this Mac), direction, endpoint name and the message bytes in hex.
// `fake-phone` stands in for a phone: it echoes what it receives, so the tool can be tried without one.
// See docs/computer-pad-usb-check.md.

import CoreMIDI
import Foundation

setvbuf(stdout, nil, _IOLBF, 0)

let positions = 80

/// Light address of each position, as the app numbers them: 64 pads from the top left, 8 top buttons, 8 right buttons.
func lightAddress(_ index: Int) -> (status: UInt8, number: UInt8) {
    switch index {
    case 0..<64: return (0x90, UInt8(10 * (8 - index / 8) + index % 8 + 1))
    case 64..<72: return (0xB0, UInt8(104 + index - 64))
    default: return (0x90, UInt8(89 - 10 * (index - 72)))
    }
}

func fail(_ message: String) -> Never {
    FileHandle.standardError.write((message + "\n").data(using: .utf8)!)
    exit(2)
}

func check(_ status: OSStatus, _ what: String) {
    if status != noErr { fail("\(what) failed: OSStatus \(status)") }
}

var timebase = mach_timebase_info_data_t()
mach_timebase_info(&timebase)
func nanos(_ host: MIDITimeStamp) -> UInt64 { host * UInt64(timebase.numer) / UInt64(timebase.denom) }

let wallClock: ISO8601DateFormatter = {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return formatter
}()

func record(_ direction: String, _ endpoint: String, _ bytes: [UInt8], host: MIDITimeStamp = mach_absolute_time()) {
    let hex = bytes.map { String(format: "%02X", $0) }.joined(separator: " ")
    print("\(wallClock.string(from: Date()))\t\(nanos(host == 0 ? mach_absolute_time() : host))\t\(direction)\t\(endpoint)\t\(hex)")
}

func name(_ object: MIDIObjectRef, _ property: CFString) -> String {
    var value: Unmanaged<CFString>?
    guard MIDIObjectGetStringProperty(object, property, &value) == noErr, let text = value?.takeRetainedValue() else { return "?" }
    return text as String
}

func endpointName(_ endpoint: MIDIEndpointRef) -> String { name(endpoint, kMIDIPropertyDisplayName) }

func uniqueID(_ object: MIDIObjectRef) -> Int32 {
    var id: Int32 = 0
    MIDIObjectGetIntegerProperty(object, kMIDIPropertyUniqueID, &id)
    return id
}

func source(_ number: Int) -> MIDIEndpointRef {
    guard number >= 0 && number < MIDIGetNumberOfSources() else { fail("No source \(number); run `list`.") }
    return MIDIGetSource(number)
}

func destination(_ number: Int) -> MIDIEndpointRef {
    guard number >= 0 && number < MIDIGetNumberOfDestinations() else { fail("No destination \(number); run `list`.") }
    return MIDIGetDestination(number)
}

/// MIDI 1.0 bytes of one Universal MIDI Packet message; system exclusive data is shown as UMP words.
func bytes(of words: [UInt32]) -> [UInt8] {
    guard let first = words.first else { return [] }
    switch first >> 28 {
    case 0x1, 0x2:
        let status = UInt8((first >> 16) & 0xFF)
        let all = [status, UInt8((first >> 8) & 0x7F), UInt8(first & 0x7F)]
        switch status {
        case 0xC0...0xDF, 0xF1, 0xF3: return Array(all.prefix(2))
        case 0xF6...0xFF: return [status]
        default: return all
        }
    default:
        return words.flatMap { word in (0..<4).reversed().map { UInt8((word >> (8 * UInt32($0))) & 0xFF) } }
    }
}

/// Words per message by UMP type, so a packet holding several messages is split correctly.
func wordCount(_ word: UInt32) -> Int {
    switch word >> 28 {
    case 0x0, 0x1, 0x2, 0x6, 0x7: return 1
    case 0x3, 0x4, 0x8, 0x9, 0xA: return 2
    case 0xB, 0xC: return 3
    default: return 4
    }
}

func receive(_ list: UnsafePointer<MIDIEventList>, _ handle: ([UInt8], MIDITimeStamp) -> Void) {
    for packet in list.unsafeSequence() {
        let words = MIDIEventPacket.WordCollection(packet)
        var index = words.startIndex
        while index < words.endIndex {
            let count = min(wordCount(words[index]), words.endIndex - index)
            handle(bytes(of: Array(words[index..<index + count])), packet.pointee.timeStamp)
            index += count
        }
    }
}

final class Sender {
    private let port: MIDIPortRef
    private let target: MIDIEndpointRef
    private let label: String
    private let capacity = 64 * 1024
    private let buffer: UnsafeMutableRawPointer

    init(client: MIDIClientRef, target: MIDIEndpointRef) {
        var port = MIDIPortRef()
        check(MIDIOutputPortCreate(client, "computer pad check out" as CFString, &port), "MIDIOutputPortCreate")
        self.port = port
        self.target = target
        label = endpointName(target)
        buffer = UnsafeMutableRawPointer.allocate(byteCount: capacity, alignment: 8)
    }

    /// Sends three-byte MIDI 1.0 messages in order and logs each one. A list of at most 200 messages
    /// fits the buffer even if every message took a packet of its own (268 bytes at most).
    func send(_ messages: [[UInt8]], log: Bool = true) {
        let list = buffer.bindMemory(to: MIDIEventList.self, capacity: 1)
        for chunk in stride(from: 0, to: messages.count, by: 200).map({ messages[$0..<min($0 + 200, messages.count)] }) {
            var packet = MIDIEventListInit(list, ._1_0)
            for message in chunk {
                var word: UInt32 = 0x2000_0000 | UInt32(message[0]) << 16 | UInt32(message[1]) << 8 | UInt32(message[2])
                packet = MIDIEventListAdd(list, capacity, packet, 0, 1, &word)
            }
            let host = mach_absolute_time()
            check(MIDISendEventList(port, target, list), "MIDISendEventList")
            if log { for message in chunk { record("sent", label, message, host: host) } }
        }
    }
}

func makeClient() -> MIDIClientRef {
    var client = MIDIClientRef()
    check(MIDIClientCreateWithBlock("UniPad computer pad check" as CFString, &client, nil), "MIDIClientCreate")
    return client
}

func runLoop(seconds: Double?) {
    signal(SIGINT) { _ in exit(0) }
    if let seconds = seconds { RunLoop.main.run(until: Date(timeIntervalSinceNow: seconds)) } else { RunLoop.main.run() }
}

func list() {
    // Endpoints of other programs are listed only once this program is a client of the MIDI server.
    _ = makeClient()
    RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.2))
    print("Sources (the phone's presses arrive here; use with `log`):")
    for index in 0..<MIDIGetNumberOfSources() {
        let endpoint = MIDIGetSource(index)
        print("  \(index)\t\(endpointName(endpoint))\tendpoint \"\(name(endpoint, kMIDIPropertyName))\"\tunique id \(uniqueID(endpoint))")
    }
    print("Destinations (lights for the phone go here; use with `colors` and `flood`):")
    for index in 0..<MIDIGetNumberOfDestinations() {
        let endpoint = MIDIGetDestination(index)
        print("  \(index)\t\(endpointName(endpoint))\tendpoint \"\(name(endpoint, kMIDIPropertyName))\"\tunique id \(uniqueID(endpoint))")
    }
    print("Devices:")
    for index in 0..<MIDIGetNumberOfDevices() {
        let device = MIDIGetDevice(index)
        var offline: Int32 = 0
        MIDIObjectGetIntegerProperty(device, kMIDIPropertyOffline, &offline)
        print("  \(name(device, kMIDIPropertyName))\tmanufacturer \"\(name(device, kMIDIPropertyManufacturer))\"\tmodel \"\(name(device, kMIDIPropertyModel))\"\t\(offline != 0 ? "offline" : "online")")
    }
}

func log(sourceNumber: Int, seconds: Double?) {
    let endpoint = source(sourceNumber)
    let label = endpointName(endpoint)
    let client = makeClient()
    var port = MIDIPortRef()
    check(MIDIInputPortCreateWithProtocol(client, "computer pad check in" as CFString, ._1_0, &port) { list, _ in
        receive(list) { message, host in record("received", label, message, host: host) }
    }, "MIDIInputPortCreate")
    check(MIDIPortConnectSource(port, endpoint, nil), "MIDIPortConnectSource")
    FileHandle.standardError.write("Logging \(label); Ctrl-C ends.\n".data(using: .utf8)!)
    runLoop(seconds: seconds)
}

/// Every position in each color 0–127, then off by note off (any velocity) and by note on 0 / CC 0.
func colors(destinationNumber: Int, pauseMs: Int) {
    let sender = Sender(client: makeClient(), target: destination(destinationNumber))
    func all(_ color: Int) -> [[UInt8]] { (0..<positions).map { let a = lightAddress($0); return [a.status, a.number, UInt8(color)] } }
    let pause = { _ = usleep(useconds_t(pauseMs * 1000)) }
    for color in 0...127 { sender.send(all(color)); pause() }
    sender.send(all(5)); pause()
    sender.send((0..<positions).map { index in
        let a = lightAddress(index)
        return a.status == 0xB0 ? [0xB0, a.number, 0] : [0x80, a.number, UInt8(127 - index * 13 % 128)]
    })
    pause()
    sender.send(all(9)); pause()
    sender.send(all(0))
}

/// [messages] lights as fast as CoreMIDI takes them; the last 80 leave each position a known color.
func flood(destinationNumber: Int, messages: Int) {
    let sender = Sender(client: makeClient(), target: destination(destinationNumber))
    let rounds = max(1, messages / positions)
    let started = mach_absolute_time()
    for round in 0..<rounds {
        sender.send((0..<positions).map { index in
            let a = lightAddress(index)
            return [a.status, a.number, UInt8(1 + (round * 7 + index) % 126)]
        }, log: round == rounds - 1)
    }
    let tookMs = Double(nanos(mach_absolute_time() - started)) / 1e6
    FileHandle.standardError.write("Sent \(rounds * positions) light messages in \(String(format: "%.1f", tookMs)) ms; the last round is logged above.\n".data(using: .utf8)!)
}

/// A virtual source and destination named "UniPad check fake phone": everything sent to it comes back.
func fakePhone(seconds: Double?) {
    let client = makeClient()
    var out = MIDIEndpointRef()
    check(MIDISourceCreateWithProtocol(client, "UniPad check fake phone" as CFString, ._1_0, &out), "MIDISourceCreate")
    var input = MIDIEndpointRef()
    check(MIDIDestinationCreateWithProtocol(client, "UniPad check fake phone" as CFString, ._1_0, &input) { list, _ in
        MIDIReceivedEventList(out, list)
    }, "MIDIDestinationCreate")
    FileHandle.standardError.write("Fake phone ready; Ctrl-C ends.\n".data(using: .utf8)!)
    runLoop(seconds: seconds)
}

let arguments = Array(CommandLine.arguments.dropFirst())
func number(_ index: Int, _ what: String) -> Int {
    guard arguments.count > index, let value = Int(arguments[index]) else { fail("Missing \(what); see the top of this file.") }
    return value
}
func optional(_ index: Int) -> Double? { arguments.count > index ? Double(arguments[index]) : nil }

switch arguments.first {
case "list": list()
case "log": log(sourceNumber: number(1, "source number"), seconds: optional(2))
case "colors": colors(destinationNumber: number(1, "destination number"), pauseMs: Int(optional(2) ?? 100))
case "flood": flood(destinationNumber: number(1, "destination number"), messages: Int(optional(2) ?? 40_000))
case "fake-phone": fakePhone(seconds: optional(1))
default: fail("Usage: list | log <source> [seconds] | colors <destination> [ms] | flood <destination> [messages] | fake-phone [seconds]")
}
