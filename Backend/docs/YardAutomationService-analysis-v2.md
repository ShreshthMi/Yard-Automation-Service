# YardAutomationService — analysis and build plan

**Revision 2.** Rewritten against the confirmed scope: a listen-only advisory aid for blind-push shunting. Supersedes revision 1.

Source documents: **D3487-7** (FSE protocol 2.1), **D21008-5** (COM-FSE101 technical documentation), **62611_2_032** (Software Requirements FSE Generic).

---

## 1. Scope

Track sensors → AEB boards → COM-FSE101 → **this application** → driver's screen.

The application **listens only**. It never transmits, never commands, never resets. When a section goes faulty the yard controller performs the reset procedure by the rules already in place; this application's job is to *notice* that it happened — to see the section go faulty, and later see it come back clear.

Its purpose is to help a driver pushing a rake from behind, unable to see the leading wagon, judge how close the front is to the end of the track. Green lights all the way home.

**What this scope removes** from revision 1: no transmit path, no reset commands, no checkbyte loopback, no bidirectional handshake to build. Roughly a third of revision 1 no longer applies.

**What it does not remove:** the timestamp handshake still needs a decision (§7), because a strictly-conforming COM-FSE may mark all its data as not-yet-valid if nothing ever answers it.

**Safety position.** Only one of the twelve status bits — `CLR` — is designated vital by Frauscher, and D21008 states that for safety-related applications only that bit may be used. Everything else this application displays is non-vital diagnostic information. Combined with running on general-purpose hardware, that puts this squarely in advisory territory. Fine — but it must be stated in the product and reflected in the interface, because *"Apply Brakes, Stop Immediately!"* reads as an instruction to a driver regardless of what the architecture document says.

---

## 2. What the application does today

```
UDP :9000
   │
   ├─ UdpListener       receive, left-pad 42 zero bytes to restore sheet offsets
   ├─ PacketValidator   CRC32 + CRC32/I
   ├─ PacketDecoder     walk the field tree from protocol_definition.xlsx
   ├─ NotificationService   pick highest-index occupied section per yard
   └─ YardBroadcaster   STOMP → /topic/yard/{yardName}
```

Plus a CSV audit trail on state change, and three HTTP endpoints for yard list, zone list and protocol reload.

**The design idea worth keeping:** one spreadsheet defines both the wire format and the yard configuration, so the simulator and the receiver cannot drift apart, and adding a status bit needs no Java change. Everything below extends that schema rather than replacing it.

**Maturity:** ~1,100 lines, 20 classes, **zero tests**.

**Verified correct, leave alone:** the CRC implementation (regenerated the table from the polynomial, matched D3487 Appendix C at six index points), the CRC coverage range, the 42-byte header offsets, and the mapping of all twelve status bit positions into the 16-bit word.

---

## 3. The twelve status bits — what each one actually tells you

Every packet carries all twelve, for every configured track section. The application currently reads **one** of them (`CLR`) and discards the rest. This is the single largest pool of unused value in the system.

Layout: two bytes, big-endian, so byte 0 occupies word bits 15–8 and byte 1 occupies word bits 7–4.

### Group A — occupancy (the core state)

| Word bit | Name | Vital | 1 means | 0 means |
| --- | --- | --- | --- | --- |
| 15 | `CLR` | **yes** | section clear | section not clear |
| 14 | `OCC` | no | section occupied | section not occupied |

These two are only meaningful **together**:

| CLR | OCC | Meaning |
| --- | --- | --- |
| 1 | 0 | clear |
| 0 | 1 | occupied |
| 0 | 0 | **faulty** |
| 1 | 1 | cannot occur |

This is the correction that matters most. The application currently reads `CLR == 0` as "occupied" without consulting `OCC`, so a faulty section is displayed to the driver as an occupied one and written into the audit log as `OCCUPIED`. That is exactly the case you said you need to catch and currently don't.

`CLR` is fail-safe by design: under any fault it drops to 0. So `CLR=0, OCC=0` is the system telling you honestly that it cannot determine the state.

### Group B — is this reading trustworthy?

| Word bit | Name | Vital | 1 means |
| --- | --- | --- | --- |
| 9 | `ERR` | no | error in the track section |
| 8 | `PT` | no | partial traversing |
| 7 | `CE` | no | communication error between AEB and COM-FSE |

**`CE` must be evaluated before anything else.** D21008 carries a footnote that is easy to miss and changes the whole decode order: when a communication error exists between the AEB and the COM-FSE, *every other bit in this information is 0*. That means `CLR=0` and `OCC=0` — which looks identical to "faulty" if you check the truth table first. Check `CE` first and the two conditions separate cleanly: `CE=1` is "the sensor is unreachable", `CLR=0/OCC=0` with `CE=0` is "the sensor is reachable and reports it cannot determine the state". Different problems, different responses.

**`ERR`** is the reason code for a faulty section. It will normally coincide with `CLR=0, OCC=0` rather than contradict it.

**`PT`** — partial traversing — means a wheel came onto a sensor without completing a clean traversal. It has two quite different causes and both matter to you. It can be a genuine equipment fault (wire break, overcurrent, missing overlap). It can also be an entirely normal blind-push situation: the rake noses into a section, stops with a wheel sitting on the sensor, or eases back. Either way the counting integrity for that section is now questionable, and the driver would want to know that something is sitting on a boundary.

### Group C — what the counter itself thinks

| Word bit | Name | Vital | 1 means |
| --- | --- | --- | --- |
| 13 | `NZ` | no | counter reading is not zero |
| 10 | `WCT` | no | waiting for clearing of track |

**`NZ`** is the raw axle count being non-zero, which is *not* the same thing as the occupancy logic asserting `OCC`. Normally they agree. When they disagree — `NZ=1` with `OCC=0`, or the reverse — you have an early warning of count drift, usually the precursor to a section going faulty. Worth surfacing to the yard controller as a health indicator well before it becomes a failure.

**`WCT`** distinguishes "this section has never had anything in it" from "axles went in and the system is waiting for them to come out". For a blind push that's a real operational distinction: it tells you the section is expecting a departure.

### Group D — the reset workflow (read-only, but directly useful to you)

| Word bit | Name | Vital | 1 means |
| --- | --- | --- | --- |
| 12 | `RR` | no | reset restriction — last axle counted **in** |
| 11 | `RAB` | no | reset is possible |
| 6 | `RAC` | no | reset accepted |
| 5 | `RJO` | no | reset rejected operationally |
| 4 | `RJT` | no | reset rejected technically |

This group is worth dwelling on, because it delivers a feature you specifically want **without transmitting a single byte**.

The yard controller runs the reset procedure by his own rules. Meanwhile these five bits narrate it live in the packets you are already receiving:

- **`RAB`** tells him, *before* he tries, whether a reset is even available on that section.
- **`RR`** tells him whether the last axle event was an entry or an exit, which is what determines the permitted reset type.
- **`RAC` / `RJO` / `RJT`** tell him the outcome the moment it lands: accepted, rejected for operational reasons, or rejected for technical reasons. Rejected-operationally and rejected-technically are different conversations with different people.

So your application can show the reset happening in real time and confirm the section returning to clear, purely as an observer. That is precisely the read-only posture you described, and it turns out to be a genuine feature rather than a limitation.

### Summary

| Bit | Used today | Should be |
| --- | --- | --- |
| `CLR` | ✓ (misread — ignores `OCC`) | primary state, with `OCC` |
| `OCC` | ✗ | primary state, with `CLR` |
| `CE` | ✗ | **evaluated first**, overrides everything |
| `ERR` | ✗ | fault reason code |
| `PT` | ✗ | fault reason / boundary-occupancy indicator |
| `NZ` | ✗ | count-drift health warning |
| `WCT` | ✗ | awaiting-clearance context |
| `RR` | ✗ | reset context for controller |
| `RAB` | ✗ | reset availability for controller |
| `RAC` | ✗ | live reset outcome |
| `RJO` | ✗ | live reset outcome |
| `RJT` | ✗ | live reset outcome |

The decoder already unpacks all twelve correctly. This is entirely application-layer work — no protocol changes needed.

---

## 4. Track section state model

Derive one primary state per section, in this strict order. Order matters — getting it wrong is how a communication error gets misreported as a section fault.

```
1.  no packet from COM-FSE within timeout  →  STALE        (whole yard)
2.  CE = 1                                 →  NO_SENSOR_DATA
3.  CLR = 1 and OCC = 1                    →  INVALID      (must not occur)
4.  CLR = 0 and OCC = 0                    →  FAULTY
5.  CLR = 0 and OCC = 1                    →  OCCUPIED
6.  CLR = 1 and OCC = 0                    →  CLEAR
```

Then attach annotations, which modify the display but not the primary state:

- On `FAULTY`: reason codes from `ERR`, `PT`
- On any state: `NZ` / `OCC` disagreement → count-drift warning
- On any state: `WCT` → awaiting clearance
- On `FAULTY` / `NO_SENSOR_DATA`: `RAB`, `RR` → reset availability and context
- Any of `RAC` / `RJO` / `RJT` → live reset outcome

Three of these states — `STALE`, `NO_SENSOR_DATA`, `FAULTY` — plus `INVALID` all mean the same thing to the driver: **I do not know whether your wagons are in this section.** They mean different things to the yard controller. That distinction drives the visual design below.

---

## 5. Visual language

### 5.1 The core problem with the current colours

`ZoneColors` assigns a colour purely from a section's position in the yard: last section red, then single yellow, then double yellow, everything earlier green. That conflates two independent things — *where* a section is, and *what state* it is in. There is no colour in the palette for "faulty", "no sensor data", or "connection lost", which is why those conditions are currently invisible.

The fix is two axes. Position determines the signal aspect **only when the section is confirmed occupied and healthy.** Every abnormal state gets its own treatment outside the green–yellow–red family, because those four colours already carry signalling meaning and must not be overloaded.

### 5.2 Proposed palette

| State | Fill | Pattern | Label | Driver reads it as |
| --- | --- | --- | --- | --- |
| `CLEAR` | `#B4B2A9` | solid, low contrast | — | nothing here |
| `OCCUPIED`, 4+ sections from end | `#639922` green | solid | ZONE n OCCUPIED | proceed |
| `OCCUPIED`, 3rd from end | `#FDD835` double yellow | solid | ZONE n OCCUPIED | caution |
| `OCCUPIED`, 2nd from end | `#F9A825` single yellow | solid | ZONE n OCCUPIED | slow |
| `OCCUPIED`, last section | `#A32D2D` red | solid | ZONE n OCCUPIED | stop |
| `FAULTY` | `#7B3FA0` purple | diagonal hatch | SECTION FAULTY | **unknown — stop and ask** |
| `INVALID` | `#7B3FA0` purple | diagonal hatch | INVALID READING | **unknown — stop and ask** |
| `NO_SENSOR_DATA` (`CE=1`) | `#3F4A55` slate | cross hatch | NO SENSOR DATA | **unknown — stop and ask** |
| `STALE` (link lost) | `#3F4A55` slate | cross hatch | LINK LOST | **unknown — stop and ask** |

Purple is the conventional signalling-panel colour for failure and out-of-correspondence, and it sits well clear of the green–yellow–red family. Slate reads as "no information" rather than "bad information", which is the right instinct for the two connectivity states.

### 5.3 Three rules that matter more than the specific hex values

**Colour is never the only channel.** Every abnormal state carries a fill pattern *and* a text label, not just a hue. Drivers are screened for colour vision; yard controllers generally are not. More practically, a screen in direct sunlight loses colour separation long before it loses pattern separation.

**Abnormal must be louder than normal.** A faulty section should pull the eye harder than a green one. The hatching does most of this work — a patterned section reads as "wrong" at a glance even before you register which colour it is.

**Same severity, different diagnosis.** All four unknown-states get equally emphatic treatment, because the driver's response is identical in every case: stop and ask. But the label and hue differ, because the controller's response is completely different — `FAULTY` starts a reset procedure, `NO_SENSOR_DATA` means check the AEB link, `STALE` means check the network to the COM-FSE. This strongly suggests two views over the same data: a driver view that collapses all four into one unmistakable "unknown", and a controller view that separates them. Worth deciding early, because it shapes the message contract.

### 5.4 Link lost is a whole-yard condition

`STALE` is not a per-section state that arrives in a packet — it is the *absence* of packets. When the timeout expires the entire diagram must change: every section to the stale treatment, the whole view desaturated, and a persistent banner that cannot be dismissed. Individual sections quietly going grey is not enough. The failure mode you are guarding against is a driver glancing at a frozen screen and reading it as live.

### 5.5 Degrading the advice honestly

This is the subtle part, and it decides whether the system is trusted or ignored.

Number the sections 1..N with N closest to the buffer stop. The front of the rake is in the highest-numbered section containing any part of it.

- All sections reporting reliably → the front is in the highest `OCCUPIED` section. Straightforward.
- Some section is in an unknown state → the front *might* be in it.

The safe reading is the worst case: `advisoryPosition = max(highest OCCUPIED, highest unknown section at or beyond it)`. Show the aspect for that position, marked clearly as **assumed, not confirmed** — a dashed border and an "UNCONFIRMED" tag alongside the normal aspect.

Two consequences worth being deliberate about:

An unknown section *behind* the last confirmed occupied section does not affect where the front is, so it must not degrade the advice. It should still be drawn as unknown on the diagram — the controller needs to see it — but it should not make the driver's aspect more restrictive than reality warrants.

And resist the temptation to blank the whole yard to STOP on any single fault anywhere. In a real yard there is always one flaky sensor somewhere. A system that goes to stop every time becomes a system that gets ignored, and an ignored safety aid is worse than none. Degrade precisely, mark it honestly, and keep the rest of the display useful.

---

## 6. Link health — the silence problem

There is currently no timer watching for silence. If the COM-FSE stops sending — cable, board reset, switch failure — the application simply stops receiving, broadcasts nothing, and the driver's last message stays frozen on screen indefinitely.

D3487 is explicit that a receiver must enter its safe state on its own when no valid message is available, and its Appendix D defines the state machine: start in `Timeout`, move to `Operational` on the first valid application data, return to `Timeout` when the age of the last received packet exceeds the configured limit.

What to build:

- A configurable timeout, defaulting to a small multiple of the expected transmission interval. The COM-FSE's minimum interval is 100 ms, so something in the 300–500 ms range is a reasonable starting point.
- A per-link state machine, `Timeout ⇄ Operational`.
- On entering `Timeout`: broadcast an explicit stale message to every yard, so clients change state actively rather than by inference from silence. A client that has to notice an *absence* to know it is stale will eventually fail to.
- A visible link indicator in normal operation, not just an error state — the driver should be able to see that the link is alive, so that its absence is meaningful.

---

## 7. The timestamp handshake

Two separate problems, one in the protocol design and one in your simulator.

### 7.1 The protocol question

FSE proves data freshness with a two-way timestamp handshake. Each side stamps its packets and flags them either "my timestamp is valid, use this data" or "not yet valid, discard the application data". A device only flips that flag to *valid* after it has heard from its partner.

Read strictly, a COM-FSE that never hears from you sits at "not yet valid" indefinitely — still sending packets, but every one marked as data you should not use.

Whether this bites depends on the site configuration. If the COM-FSE is configured with your application as its communication partner, it applies. If it is configured against a real interlocking and your application is listening in on that link, the handshake completes between those two and you see valid data throughout.

Your description sounds like the first case. **Confirm this with whoever configures the COM-FSE before you build on the listen-only assumption** — it is a one-question conversation and it determines whether §7.2 is a small change or a significant one.

If it does apply, the fix is not a reset command or anything resembling control. It is a heartbeat: an FSE packet carrying zero bytes of application data, sent at intervals, purely to complete the handshake. D3487 explicitly provides for this — messages are sent in both directions at regular intervals even when there is no application data to carry. That preserves your position completely: the application still never tells the axle counter to do anything.

### 7.2 The simulator is hiding this from you

`PacketBuilder` hardcodes three fields, and the combination is not one a real COM-FSE would produce at power-up:

```java
p[11] = (byte) 2;   // RX Timestamp Control  — "I have never received anything from you"
putUnsignedBE(p, 12, 4, 0);   // RX Timestamp — always zero
p[16] = (byte) 0;   // TX Timestamp Control  — "my timestamp is valid"  ← always
putUnsignedBE(p, 17, 4, state.nextTxTimestamp());
```

Real hardware starts with the TX control byte at 1 (not yet valid) and only moves to 0 after the handshake. The simulator starts at 0 and stays there, so **your application has never once been tested against data marked not-yet-valid.** The one case that will bite you at site is the one case you cannot currently produce.

The TX timestamp itself is also a packet counter, incrementing by one per packet regardless of elapsed time, despite the comment describing each tick as 10 ms. That means it does not track real time, so it cannot be used to test message-age or timeout logic.

Meanwhile the application decodes all three fields — they are in the core sheet — and reads none of them. Neither half of the pair works, and they need fixing together.

What the simulator needs:

- Model the real power-up sequence: start `TTC=1, TT=0`, with a command to advance the handshake, and a switch for whether it ever converges (so you can reproduce the listen-only case from §7.1 deliberately).
- Drive the TX timestamp from a real clock at 10 ms resolution rather than a packet counter.
- Commands to stop sending (timeout), replay a packet (duplicate), and send out of order (resequence).
- First-class fault commands per section: `CE=1` including the all-other-bits-zero behaviour from §3, `CLR=0/OCC=0` faulty, `ERR=1`, `PT=1`. Manual bit overrides can already produce most of these, but making them explicit commands means your fault handling gets exercised routinely rather than by someone remembering the right incantation.
- A reset-sequence command that walks `RAB` → reset attempt → `RAC` or `RJO`/`RJT` → back to clear, so the controller view of §3 Group D can be developed and demonstrated without waiting for a real fault at site.

---

## 8. Remaining defects

| # | Location | Issue |
| --- | --- | --- |
| 1 | `PacketValidator:29` | Length guard `crc32InverseOffset + 4 > total` reduces to `total > total` — always false, dead code. **Reproduced:** a datagram of exactly 8 zero bytes is *accepted*, because the CRC range collapses to empty and both computed and received CRCs are 0. It decodes to an empty payload and flips every yard to an error state. D3487 requires discarding anything under 29 application-layer bytes; add that check and the configured-length check alongside it. |
| 2 | `NotificationService:108` | `findByKey` returns the first match in the tree. A duplicated identifier in the sheet collapses to a list, `extractClr` returns null for a list, and the section silently reports **not occupied** — a silent failure in the occupancy path with no warning anywhere. Validate identifier uniqueness at sheet load. |
| 3 | `NotificationService:51,66` | Full recursive tree walk per section, twice per section per packet. Fine at three sections; at the spec ceiling of 40 AEBs it is 160 walks per packet. Resolve identifiers to fixed paths once at load. |
| 4 | `CoreLayout:17` | `r.offset.startsWith(...)` with no null check — a blank Offset cell in the core sheet is an NPE at packet time rather than a clear error at load time. |
| 5 | `UdpListener:68` | Decode, CSV write and WebSocket broadcast all run on the UDP receive thread. A slow client or blocking disk write applies backpressure straight to the socket buffer, and dropped datagrams are invisible — no counter, no metric. |
| 6 | `RejectedPacketLogger:20` | Logs full packet hex at INFO with no rate limit. A malformed-packet flood fills the disk. |
| 7 | `YardZonesController`, `WebSocketConfig` | `origins="*"` everywhere, no authentication. |
| 8 | `ProtocolReloadController` | Unauthenticated `POST /api/protocol/reload` accepting an arbitrary filesystem path. Anyone on the network can re-point a running system at another file. |
| 9 | design-wide | The yard *name* is the identity — WebSocket topic, REST path segment, CSV column. `"Test Yard 1"` puts spaces into a STOMP destination and a URL, and renaming a yard silently breaks every subscribed client and splits the audit trail. Needs a stable id with the name as a display label. |
| 10 | `pom.xml` | No actuator, so no health or metrics endpoint. |

Also to correct while in the area: `CATS` and `TL` are transmitted as **signed** integers per D21008, and `PacketDecoder` decodes every numeric field as unsigned.

---

## 9. Build order

### Phase 0 — safety net

No tests exist. Everything below changes packet-path behaviour, so this comes first, and it is cheap because the interesting logic is already pure functions. CRC vectors from D3487 Appendix C, golden-packet fixtures captured from the simulator, and truth tables for zone colours and current-message selection.

### Phase 1 — see the truth

The headline work, and the thing you specifically asked for.

- Implement the §4 state model with `CE` evaluated first.
- Decode and surface all twelve bits.
- Correct `CLR`/`OCC` handling — three distinct states, not two.
- Signed `CATS` / `TL`.
- Fix defects 1–4 and 6.
- Audit trail records the real state, including `FAULTY` and `NO_SENSOR_DATA` as distinct from `OCCUPIED`.

### Phase 2 — link health

Timeout detection, `Timeout ⇄ Operational` state machine, explicit stale broadcast, live link indicator. Resolve the §7.1 question and add the heartbeat if it turns out to be needed.

### Phase 3 — the visual language

Implement §5. Two views if you go that way — driver and controller. Unconfirmed-position marking. Whole-yard stale treatment.

### Phase 4 — simulator parity

Everything in §7.2. Strictly this could come earlier — it is what lets you *test* phases 1–3 properly, and there is a reasonable argument for pulling the fault commands forward into Phase 1.

### Phase 5 — the other four data types

Direction, speed, wheel diameter, AEB I/O. Direction is the valuable one: it tells you a movement is happening and which way, which is currently inferred from occupancy changes. Speed lets you check whether "reduce to 15 km/h" was actually followed. See the companion field reference for the byte layouts and formulas.

### Phase 6 — production

Authentication, origin lockdown, actuator health and metrics, stable yard ids, log rotation and disk guards, config validation with a dry-run endpoint.

---

## 10. Open questions

Reduced to four, now that scope is settled.

1. **Is the COM-FSE configured with this application as its communication partner, or is it talking to a real interlocking with us listening in?** §7.1. One question to whoever configures the board, and it determines whether a heartbeat is needed.
2. **Driver view and controller view, or one view for both?** §5.3. They want genuinely different things from the same data, and this shapes the message contract, so decide before building the frontend.
3. **How many COM-FSE links?** The protocol is multi-instance by destination port. Designing for one and retrofitting many is painful.
4. **Is `Blind-push-demo.html` the basis for the product UI, or scaffolding?** It is a capable harness — live track diagram, per-zone axle counts, audible last-zone alarm — but it is a single file against `origins="*"`.
