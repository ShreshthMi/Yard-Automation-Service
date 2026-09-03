# BPSolution — file-by-file reference

Companion to the analysis document. Every file in the package, what it does, and where the issues sit. Line references match the source as shipped.

Cross-references like *(analysis §4)* point at the revision-2 analysis document.

---

## Layout

```
BPSolution/
├── protocol_definition.xlsx        the configuration that drives everything
├── Blind-push-demo.html            standalone browser test client
├── BlindPushCommands.txt           launch commands, scratch notes
│
├── YardAutomationService/          the receiver — Spring Boot
│   ├── pom.xml
│   ├── README.md
│   ├── rejected-packets.log        runtime output, currently empty
│   └── src/main/
│       ├── java/com/frauscher/
│       │   ├── YardAutomationServiceApplication.java
│       │   ├── protocol/           sheet parsing + packet decoding   (8 files)
│       │   ├── ingest/             UDP receive + validation          (4 files)
│       │   ├── yard/               yard/zone configuration           (5 files)
│       │   ├── notification/       per-yard message construction     (3 files)
│       │   ├── websocket/          STOMP transport                   (2 files)
│       │   ├── controller/         HTTP endpoints                    (2 files)
│       │   └── audit/              CSV event trail                   (1 file)
│       └── resources/
│           ├── application.properties
│           └── logback-spring.xml
│
└── FSESimulator/                   the sender — plain Java CLI
    ├── pom.xml
    ├── dependency-reduced-pom.xml  build artifact
    ├── README.md
    ├── com/                        stray compiled classes — see §6
    └── src/main/java/com/frauscher/fse/simulator/
        ├── Main.java
        ├── app/, command/, config/, crc/, model/, net/, packet/
```

No `src/test` directory exists in either project.

---

## 1. Shared assets

### `protocol_definition.xlsx` — 12.9 KB

The single most important file in the package. Two sheets, and both projects read it at startup.

**Sheet "core definition"** — the FSE frame layout, columns `Parent | Field | Type | Offset | Length`. Offsets are expressed against a *full captured Ethernet frame*, so Protocol Version sits at 42 rather than 0. Two offsets are expressions rather than numbers: `Last-8` for CRC32 and `Last-4` for CRC32 Inverse, resolved against the actual packet length at decode time. The Payload row uses `Remaining-8` for its length. The sheet is marked "DO NOT MODIFY THIS SHEET".

**Sheet "payload definition"** — the application data, columns `Parent | Field | Identifier | Type | Offset | Length | Bit | Yard Name | Occupied Message 1 | Occupied Message 2`.

The last three columns are what makes this file interesting: **yard configuration lives in the same rows as the wire format.** An identifier row carrying a Yard Name becomes a track section in that yard; the two message columns are what gets shown when it is occupied. Sheet order is zone order — first row for a given yard name is zone 1, and so on toward the buffer stop. Leave Yard Name blank and the row still decodes, it just isn't surfaced to the yard layer.

Both sheets are read as a pre-order walk of a tree: each row's Parent names the Field of the row it sits under, and a group's children are the rows immediately following it whose Parent matches. That is why a dropped row shifts everything after it, and why `ConfigLoader` warns loudly rather than skipping quietly.

Current contents: one yard ("Test Yard 1"), three sections (1T, 2T, 3T), a one-byte Check Byte with ACLB/SR/A1, and two AEB_FMA groups. Each FMA carries a 2-byte Status group with all twelve bits, plus CATS and TL.

> **Note.** The twelve status bit positions are correct against D21008 Table 3.3. Eleven of the twelve are decoded and then discarded by the application *(analysis §3)*.

### `Blind-push-demo.html` — 38.9 KB

A single-file browser client, no build step. Loads SockJS and Stomp from CDN with a fallback, fetches the yard list from `/api/yards`, populates a dropdown, fetches `/api/yards/{id}/zones` on selection, then subscribes to `/topic/yard/{name}`.

Considerably more capable than "demo" suggests. It renders a live track diagram with a rail strip and sleepers, per-zone axle counts, a proportionally-sized last zone (`LAST_ZONE_FLEX = 0.3`, deliberately narrower because it is the critical one) and a fixed-width boundary wall. It tracks CATS trends per identifier to work out whether the rake is arriving or departing, flips a direction hint accordingly, and runs an audible alarm when the last zone is being approached — suppressed the moment CATS starts decreasing, since a draining last zone means departure, not danger. It handles the browser autoplay policy by unlocking audio on the first click anywhere. It reconnects on a 3-second timer.

> **Note, and it matters.** The demo already scans `ERR` and `CE` across every zone (`findErrorState`, lines 641–658) and drives a UI-wide error lockout. **The frontend currently does more fault handling than the backend does.** That logic is in the wrong place — every future client would have to reimplement it, and each could get it subtly different. It belongs server-side in the state model *(analysis §4)*, delivered as a resolved state rather than raw bits.
>
> It also inherits the backend's core bug: `findActiveIndex` (line 594) treats `CLR === 0` as occupied without consulting `OCC`, so a faulty section reads as an occupied one here too.
>
> `WS_URL` is hardcoded to `localhost:8080` at line 138, with a comment saying to edit it in place.

### `BlindPushCommands.txt` — 433 bytes

Scratch notes: the two launch command lines for simulator and service, with Windows paths. Not documentation so much as a copy-paste buffer. Worth folding into the READMEs.

---

## 2. YardAutomationService

### 2.1 Entry point and build

**`YardAutomationServiceApplication.java`** (456 B) — bare Spring Boot main class. `@SpringBootApplication` plus `@ConfigurationPropertiesScan` to pick up `AppProperties`. Nothing else.

**`pom.xml`** (2.1 KB) — Spring Boot 3.2.5 parent, Java 17. Dependencies: `spring-boot-starter-web`, `spring-boot-starter-websocket`, `poi-ooxml` 5.2.5 for the spreadsheet, `spring-boot-configuration-processor`, and `spring-boot-starter-test`. Builds to `target/YardAutomationService.jar`.

Carries a substantial comment explaining why `log4j-core` is deliberately absent — POI pulls in `log4j-api` transitively and prints a harmless "using SimpleLogger" message; adding `log4j-core` to silence it collides with the version POI bundles and throws `NoClassDefFoundError`. Leave it.

> **Note.** `spring-boot-starter-test` is declared but entirely unused — no test sources exist. No actuator, so no health or metrics endpoint *(analysis §8, defect 10)*.

**`README.md`** (16.4 KB) — genuinely good documentation. Covers the processing pipeline, project structure, yard configuration via spreadsheet columns, zone colour rules, runtime reload, the audit trail format and the deliberate absence of a CSV header row. Accurate against the code as shipped. Will need revising as the state model lands.

**`rejected-packets.log`** — runtime output, currently zero bytes. Should not be in version control.

### 2.2 `protocol/` — sheet parsing and packet decoding

**`Row.java`** (2.8 KB) — one spreadsheet row. All fields public final. `offset` and `length` are kept as **Strings** rather than ints, because they can be expressions (`Last-8`, `Remaining-8`) as well as plain numbers. Blank cells normalise to null, everything else is trimmed. Carries `excelRow` for diagnostics.

**`FieldNode.java`** (339 B) — a Row plus its children. Children only exist for `type = "group"`. Nine lines of actual code.

**`TreeBuilder.java`** (1.1 KB) — turns the flat ordered row list into a tree in a single pass, using a shared cursor (`int[] cursor`) so recursion advances one shared read position. Elegant, and depends entirely on the sheet being written in strict pre-order.

**`ConfigLoader.java`** (6.6 KB) — reads both sheets via POI. Two subtleties worth knowing about. First, POI sheets become unusable once the source Workbook closes, so `cloneSheetInMemory` copies every cell into a fresh in-memory workbook with formulas already evaluated to strings. Second, the row-dropping policy: rows with both Parent and Field blank are skipped silently (notes, spacers), but a row with Parent/Field present and an unreadable Type produces a loud warning naming the exact Excel row — because a dropped row shifts the interpretation of everything after it.

> **Note.** Warnings go to `System.err` rather than the logger, so they bypass logback entirely and won't appear in any log file.

**`CoreLayout.java`** (1.1 KB) — looks up a top-level field's byte offset from the core rows. Two overloads: one for plain numeric offsets, one that also resolves `Last-N` given the packet's total length.

> **Note.** Line 17, `r.offset.startsWith("Last-")` with no null guard. A blank Offset cell on a core row is an NPE at packet-processing time rather than a clear error at load time *(analysis §8, defect 4)*.

**`Crc32.java`** (1.9 KB) — reflected CRC-32 with the CRC-32Q polynomial (`0xD5828281` reflected), init `0xFFFFFFFF`, xorout `0xFFFFFFFF`. `computeInverse` runs the same algorithm with each byte bitwise-inverted first.

> **Verified correct.** Table regenerated from the polynomial and matched against the reference C implementation in D3487 Appendix C at six index points. This file needs no work.

**`PacketDecoder.java`** (7.3 KB) — the heart of the decode path. `buildFullTree` assembles the core tree and grafts the payload tree onto the node named "Payload". `decode` walks that tree against the raw bytes and produces a `LinkedHashMap`/`List`/`Long`/`String` structure that Jackson serialises directly.

Three behaviours worth knowing. Fields whose bytes don't fit decode to `null` with a warning rather than throwing, so a short packet degrades instead of exploding. Repeated keys within a group collapse into a `List`. And JSON key naming differs by origin — core-sheet names and all group names get camel-cased (`"Internet Protocol"` → `internetProtocol`), while payload-sheet leaf fields stay verbatim, because `CATS`, `TL` and `CLR` read as protocol codes rather than English.

> **Note.** Line 90 decodes every `int`/`long` field with `unsignedBigEndian`. D21008 specifies CATS and TL as **signed** integers *(analysis §8)*.

**`ProtocolRegistry.java`** (2.0 KB) — holds the active configuration and makes runtime reload safe. Core rows, packet tree and yard list are bundled into one immutable `Snapshot` record and swapped through a single `AtomicReference`, so a reader mid-packet never sees a mix of old and new. A failed reload throws and leaves the previous snapshot running untouched. Constructor loads at startup and will refuse to start on a bad path.

Small and correct. This is the piece that makes hot reload trustworthy.

### 2.3 `ingest/` — receive and validate

**`UdpListener.java`** (4.1 KB) — `ApplicationRunner` that spawns one daemon thread on startup and loops on `socket.receive`. Each datagram is left-padded with zero bytes up to the Protocol Version offset, because the sheet's offsets assume a full captured frame while the OS strips those headers from a real socket. Reads the snapshot fresh from `ProtocolRegistry` on **every packet**, not once at thread start — which is precisely what makes hot reload take effect on the next packet.

> **Note.** Validation, decode, CSV write and WebSocket broadcast all happen inline on this one receive thread. A slow client or a blocking disk write applies backpressure directly to the socket buffer, and dropped datagrams are invisible — no counter, no metric *(analysis §8, defect 5)*.

**`PacketValidator.java`** (2.4 KB) — checks CRC32 and CRC32 Inverse against the values in the packet, resolving all three offsets from the current core rows.

> **Note, significant.** Line 29's length guard, `crc32InverseOffset + 4 > total`, reduces to `total > total` and is therefore always false — dead code. The only effective floor is `crc32Offset < protocolVersionOffset`. I reproduced the consequence: a datagram of exactly 8 zero bytes is **accepted**, because the CRC range collapses to empty and both computed and received CRCs are 0. It then decodes to an empty payload and flips every yard into an error state *(analysis §8, defect 1)*.

**`ValidationResult.java`** (385 B) — a record of `(boolean valid, String reason)` with two static factories. Three lines of logic.

**`RejectedPacketLogger.java`** (703 B) — writes to a named SLF4J logger `REJECTED_PACKETS`, which `logback-spring.xml` routes exclusively to its own file. Logs size, reason and the full packet hex.

> **Note.** No rate limit and no deduplication. A malformed-packet flood fills the disk *(analysis §8, defect 6)*.

### 2.4 `yard/` — configuration derived from the sheet

**`AppProperties.java`** (890 B) — `@ConfigurationProperties(prefix = "app")` record binding `protocolDefinitionPath` and a nested `Udp(int port)`. Carries a comment noting that yard configuration deliberately no longer lives here.

**`YardConfig.java`** (477 B) — record of `(String name, List<TrackSectionConfig> trackSections)`. The name is the yard's only identifier — there is no separate id.

**`TrackSectionConfig.java`** (345 B) — record of `(String name, String clrMessage, String clrWarning)`. `name` is the sheet identifier such as `1T`.

**`YardDiscovery.java`** (1.6 KB) — walks the payload rows, and any row with both an identifier and a yard name becomes a track section grouped under that yard, preserving sheet order via `LinkedHashMap`. Thirty lines that replace what used to be a separate properties file.

**`ZoneColors.java`** (1.5 KB) — assigns a colour purely from position: last section red, one before single yellow, one before that double yellow, everything earlier green, plus a neutral for "nothing occupied anywhere". Works for any section count.

> **Note.** This is the file that needs the most rework. Colour is derived from position alone, with no concept of section *state* — which is why faulty sections, unreachable sensors and lost links are currently invisible *(analysis §5)*.

### 2.5 `notification/` — building the per-yard message

**`NotificationService.java`** (4.5 KB) — the application logic. For each yard, feeds every section's decoded node to `TrackEventLogger`, builds a `CurrentMessage`, and broadcasts. `buildCurrentMessage` scans sections in order and keeps the **highest-index** occupied one, since sections are ordered by proximity to the buffer stop. If any configured identifier is missing from the packet it refuses to guess and returns an error instead — deliberately conservative, because the rake could be in the section whose data is missing.

That conservatism is the right instinct and should be extended to faulty sections *(analysis §5.5)*.

> **Notes.** Line 72 reads `CLR == 0` as occupied without consulting `OCC`, so a faulty section is reported as occupied *(analysis §3, Group A)* — the single most important correctness fix in the package. Line 108's `findByKey` returns the first match in the tree, so a duplicated identifier collapses to a `List`, `extractClr` returns null, and the section silently reports not-occupied *(analysis §8, defect 2)*. And the tree is walked twice per section per packet *(defect 3)*.

**`CurrentMessage.java`** (923 B) — record of `(message, warning, error, color)`. The class comment documents its three states clearly: a zone occupied, nothing occupied anywhere, or a configured identifier missing. Worth reading before extending it, because the state model in the analysis adds several more.

**`YardMessage.java`** (749 B) — the WebSocket envelope: `(yardName, payload, currentMessage)`. The payload is the decoded packet forwarded **as-is**, every identifier the sheet defines whether or not it belongs to a yard. `currentMessage` is precomputed server-side so every client shows the same thing without re-deriving it.

That design intent — resolve once on the server, don't make clients interpret raw bits — is exactly right, and exactly what the demo HTML is currently violating by scanning ERR/CE itself.

### 2.6 `websocket/` — transport

**`WebSocketConfig.java`** (1.4 KB) — enables a simple in-memory broker on `/topic` and registers `/ws` as a STOMP-over-SockJS endpoint. Subscribing to one yard's topic structurally cannot receive another's, so no manual filtering is needed.

> **Note.** `setAllowedOriginPatterns("*")` to let the `file://` demo page connect. The comment already says to tighten it for production *(analysis §8, defect 7)*.

**`YardBroadcaster.java`** (637 B) — one method wrapping `SimpMessagingTemplate.convertAndSend("/topic/yard/" + name, message)`.

> **Note.** The yard *name* becomes the topic name. `"Test Yard 1"` puts spaces into a STOMP destination, and renaming a yard in the spreadsheet silently breaks every subscribed client *(analysis §8, defect 9)*.

### 2.7 `controller/` — HTTP

**`YardZonesController.java`** (3.3 KB) — `GET /api/yards` returns `[{id, name}]` with both keys carrying the same value, kept for client compatibility. `GET /api/yards/{yardId}/zones` returns identifier, zone number and colour per section, 404 on an unknown yard. Both read live from `ProtocolRegistry`, so a reload is reflected immediately. The intended pattern is that a frontend fetches these once for static structure and combines them with the live WebSocket payload.

**`ProtocolReloadController.java`** (2.5 KB) — `GET /api/protocol/current` reports path, core row count and yard count. `POST /api/protocol/reload` reloads, optionally from a new path, returning 400 with the reason on failure and leaving the running configuration untouched.

> **Note, significant.** Unauthenticated, `@CrossOrigin(origins = "*")`, and accepts an arbitrary filesystem path. Anyone reachable on the network can re-point a running system at a different file, and the error messages leak filesystem information *(analysis §8, defect 8)*.

### 2.8 `audit/` — the event trail

**`TrackEventLogger.java`** (5.5 KB) — writes `dateTime,yardName,trackName,status,CATS` to the `TRACK_EVENTS` logger, which logback routes to a daily CSV.

The change-detection design is careful and worth preserving. It keeps a checksum per `(yard, track)` of `status|CATS` and writes only when that changes — so every distinct CATS value gets a line as axles arrive (1, 2, 3, …), while an identical repeated reading never duplicates. First observation of a track is logged only if it starts out abnormal; an ordinary CLEAR isn't worth a line just because the service happened to start watching then.

Status is a single value with an explicit priority: `ERR` beats `CE` beats `OCCUPIED`/`CLEAR`.

The comment explaining the deliberate absence of a header row is worth reading — a header written at startup would reappear after every restart, scattered mid-file.

> **Note.** Line 101 has the same `CLR == 0 → OCCUPIED` bug, so **the audit trail records faults as occupancies**. That is worse here than on the display, because the log is what gets read months later during an incident review, when nobody can tell it was wrong *(analysis §3)*.

### 2.9 `resources/`

**`application.properties`** (941 B) — three properties with working defaults: `app.protocol-definition-path=protocol_definition.xlsx`, `app.udp.port=9000`, `app.audit-log-path=.`. Well commented, including a note that yard configuration deliberately isn't here.

**`logback-spring.xml`** (2.9 KB) — three appenders. Console for the root logger; `REJECTED_FILE` for the `REJECTED_PACKETS` logger; `TRACK_EVENTS_FILE` for `TRACK_EVENTS`. Both audit loggers use `additivity="false"` so their entries never leak into the console log.

One deliberate trick: the track-events appender has **no** `<file>` element, only a `fileNamePattern`. That makes Logback write directly to today's dated file from the first line, instead of an undated file that only acquires a date at midnight rollover.

> **Note.** The XML comment describing the appender is stale — it claims `TrackEventLogger` writes a header line with six columns. It doesn't write a header at all, and the format has five columns. The Java comment is correct; this one was not updated.

---

## 3. FSESimulator

A plain Java CLI, no Spring. Sends only the real wire payload — Protocol Version through CRC32 Inverse — since Ethernet/IP/UDP headers are added by the OS, not the application.

**`Main.java`** (2.4 KB) — argument parsing (`<xlsx> <host> <port>`), loads both sheets, builds the payload tree, computes header and payload lengths, discovers axle slots, wires up state, builder and sender by constructor, then hands off to `Simulator.run()`. Fails fast if no identified groups are found in the sheet.

**`app/Simulator.java`** (17.3 KB) — by some distance the largest file in the package, and the most interesting. The `sim>` command loop: parse, validate identifiers and bit names against what was actually discovered in the sheet, mutate state, build a fresh packet, send.

The substantial part is `handleSimulateTrain` (line 203 onward), which models a blind push properly rather than as a sequence of single-zone events. Track sections are laid end to end into one strip, each occupying `[start, end)`. The train's body at any instant is the interval `[front − length, front)`. Each zone's axle count is simply how much of that interval overlaps its own span, recomputed from a single front position every step. Several zones changing at once falls out naturally, with no need to pace zones on independent schedules — one zone drains while the next fills, which is the whole point.

The front advances to the end of the strip, pauses, then retreats the same way using the same per-zone delays. The class comment is explicit that a zone still partially occupied when the front reaches the wall is correct physics for the given numbers, not a bug.

**`command/Command.java`** (3.3 KB) — an immutable parsed command. One `Type` enum, fields for every command shape, private constructor with static factories per shape. Fields irrelevant to a given type are simply null.

**`command/CommandParser.java`** (6.5 KB) — regex and token parsing, deliberately strict: required keywords (`to`, `from`, `for`, `on`, `delay`) must appear exactly where documented, which keeps handling unambiguous. Pure parsing, no identifier validation and no side effects — validation needs the live identifier set, which belongs to `Simulator`. Default pause for `simulate train` is 3000 ms.

**`config/`** — `Row`, `FieldNode`, `TreeBuilder`, `ConfigLoader`, `CoreLayout`. Copies of the service's equivalents. See §6.

**`crc/Crc32.java`** (2.0 KB) — functionally identical to the service's copy; the only differences are tabs versus spaces.

**`model/AxleSlot.java`** (1.8 KB) — the discovered layout of one track slot. Every field except `offset` is nullable, where null means "this identifier's definition doesn't have that field". Whatever is present gets written, whatever is missing is skipped — never an error. A track without CATS or TL is perfectly valid.

**`model/SlotDiscovery.java`** (3.7 KB) — walks the payload tree for identified groups at any depth and builds an `AxleSlot` for each, descending through unidentified wrapper groups. `computePayloadLength` derives the payload's own byte length from the furthest `offset + length` reached by its direct children.

Notably, it warns when an identifier has no Status group ("it'll accept commands but nothing will change in the packet") or is missing CLR/OCC ("that bit won't auto-set from axle count"). Good diagnostics.

**`net/UdpSender.java`** (785 B) — one `DatagramSocket` to one fixed destination. `AutoCloseable`. Twenty lines.

**`packet/SimulatorState.java`** (2.1 KB) — mutable state: axle count and manual bit overrides per identifier, plus the TX timestamp. `setAxleCount` sets directly rather than by delta, used by `simulate train` where the value is computed from overlap rather than accumulated. Bit overrides persist until changed.

> **Note.** `nextTxTimestamp()` (line 71) returns the current value and increments by **one per packet**, despite the comment describing each tick as 10 ms. It is a packet counter, not a clock, so it cannot be used to test message-age or timeout logic *(analysis §7.2)*.

**`packet/PacketBuilder.java`** (4.1 KB) — assembles the wire payload from state and the discovered slot layout. Header fields are hardcoded constants (protocol version 2, destination address 3401, source address 3466, ports 10 and 66). Payload bytes default to zero, then each slot is written. Status bits resolve in a clear precedence: manual override wins, else CLR/OCC derive from axle count, else zero. CATS and TL are written only if the slot has them, clamped to their field width. CRCs are computed last over everything preceding.

> **Note, significant.** Lines 43–46 hardcode the timestamp control bytes: RX control to 2 ("never received anything from you"), RX timestamp to 0, and **TX control to 0 ("my timestamp is valid") permanently**. Real hardware starts TX control at 1 and only moves to 0 once the handshake completes, so the receiver has never once been tested against data marked not-yet-valid — the exact case likely to bite at site *(analysis §7)*.

**`README.md`** (7.2 KB) — accurate and useful. Class-by-class table, full command syntax, the bit-value precedence rules, and the same log4j warning as the service.

**`pom.xml`** (1.9 KB) — plain Maven, Java 17, POI 5.2.5, shade plugin producing a single runnable `FSESimulator.jar` with `Main` as entry point.

---

## 4. Build and IDE artifacts

Present in both projects, none of which should be in version control:

| Path | What it is |
| --- | --- |
| `.classpath`, `.project`, `.settings/` | Eclipse project metadata |
| `.factorypath` | Eclipse annotation-processor config (service only, 7.9 KB) |
| `target/` | Maven build output, including compiled classes and jars |
| `FSESimulator/dependency-reduced-pom.xml` | Generated by the shade plugin |
| `YardAutomationService/rejected-packets.log` | Runtime output, currently empty |

**`FSESimulator/com/frauscher/...`** deserves a specific mention: a **second, stale copy of the compiled classes sitting at the project root**, outside `target/`. Compare timestamps and it predates the `target/classes` copies — the `Simulator.class` at the root is 10.4 KB against 19.2 KB in `target/`, so it is from before `simulate train` was added. Delete it; it is pure confusion waiting to happen.

A `.gitignore` covering all of the above is a five-minute job worth doing before anyone else clones this.

---

## 5. Where each concern lives

Quick lookup when you need to change something.

| To change… | Edit |
| --- | --- |
| Packet layout, offsets, bit positions | `protocol_definition.xlsx`, core or payload sheet |
| Which sections belong to which yard | `protocol_definition.xlsx`, Yard Name column |
| Occupied messages and warnings | `protocol_definition.xlsx`, Occupied Message 1 / 2 |
| Zone colours | `ZoneColors.java` |
| Which section "wins" when several are occupied | `NotificationService.buildCurrentMessage` |
| What counts as occupied / clear / faulty | `NotificationService.extractClr` **and** `TrackEventLogger.currentStatus` — both, they duplicate the logic |
| WebSocket message shape | `YardMessage`, `CurrentMessage` |
| Audit CSV columns | `TrackEventLogger.writeEvent` (format) + `logback-spring.xml` (file naming) |
| UDP port, sheet path, log directory | `application.properties` or command-line override |
| Simulator header field values | `PacketBuilder` constants, lines 20–24 |

That "both, they duplicate the logic" row is a small design smell worth fixing while implementing the state model — occupancy interpretation should exist in exactly one place.

---

## 6. Duplication between the two projects

Six classes exist in both projects as near-copies:

| Class | Service package | Simulator package | Status |
| --- | --- | --- | --- |
| `Crc32` | `protocol` | `crc` | Functionally identical (whitespace only) |
| `FieldNode` | `protocol` | `config` | Functionally identical |
| `TreeBuilder` | `protocol` | `config` | Functionally identical |
| `CoreLayout` | `protocol` | `config` | Functionally identical |
| `Row` | `protocol` | `config` | **Already diverged** |
| `ConfigLoader` | `protocol` | `config` | **Already diverged** |

`Row` and `ConfigLoader` have drifted: the service's versions carry the three yard columns, the simulator's do not. The divergence is currently harmless — the simulator has no use for yard names — but it demonstrates the hazard rather than avoiding it. The two projects now parse the same spreadsheet with two different parsers, and any future schema change has to be made twice, correctly, in both.

The whole value proposition of this design is that the simulator and the receiver cannot disagree about the wire format. Two independently maintained copies of the parser quietly undermines that.

**Suggested fix:** extract a small `fse-protocol-common` module containing `Row`, `FieldNode`, `TreeBuilder`, `ConfigLoader`, `CoreLayout` and `Crc32`, and have both projects depend on it. Roughly a day's work including build changes, and it should happen before the sheet schema is extended for the remaining application-data types — otherwise that extension has to be written twice too.
