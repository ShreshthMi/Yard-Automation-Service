# Protocol field reference — missing application-data types in sheet schema

A working reference for Phase 2 and Phase 3. Every structure below is defined in D21008-5 (COM-FSE101) but is **not** yet in `protocol_definition.xlsx`, expressed in the row format the sheet already uses so it can be pasted in and extended.

Sheet columns, for reference:

```
Parent | Field | Identifier | Type | Offset | Length | Bit | Yard Name | Occupied Message 1 | Occupied Message 2
```

Conventions carried over from the existing sheet:

- Multi-byte fields are big-endian; `Offset` is relative to the parent group's start.
- `bit` rows carry no Offset/Length — bit position is counted from the LSB of the enclosing group read as one big-endian word. For a 1-byte group, byte bit 7 = word bit 7. For a 2-byte group, byte 0 bit 7 = word bit 15 (this is how the existing Status group maps).
- Only identifier rows take `Yard Name` / `Occupied Message 1` / `Occupied Message 2`.

---

## Already modelled (for contrast)

| Structure | Bytes | Per | In sheet |
| --- | --- | --- | --- |
| Checkbyte (FAdC → safety) | 1 | link | ✓ |
| Status of track section | 2 | track section | ✓ |
| CATS — current axles in section | 2 | track section | ✓ *(decode as **signed**)* |
| TL — train length | 2 | track section | ✓ *(decode as **signed**)* |

---

## 0. Counting heads are a second entity — read this before adding anything

The four missing FAdC → safety types do **not** attach to track sections. Three of them attach to **counting heads**, and one attaches to an AEB board.

A counting head is a wheel sensor bolted to the rail at one point. A track section is the *stretch between* two of them. Occupancy, `CATS` and `TL` are properties of the stretch; direction, speed and wheel diameter are measured *at* a point as a wheel physically crosses it. Frauscher's own ordering table reflects this — track section information and counting head information are separate blocks, listed per AEB, with counting heads labelled `ZP` (*Zählpunkt*, counting point).

Your sheet currently has exactly one kind of identifier: an `FMA` group carrying a track section id such as `1T`. Adding these types means introducing a second kind. That is a schema change, not just more rows, and it touches more code than it first appears.

### What it affects

**`YardDiscovery`** treats *any* identifier row with a Yard Name as a track section. Counting head rows would be silently swept up as bogus track sections. The sheet needs a way to say which kind an identifier is — a new `Kind` column with values like `section` / `head` is the cleanest option, and it fails safe: existing rows with a blank Kind can default to `section`.

**`findByKey` in `NotificationService`** resolves identifiers by name across the whole decoded tree, first match wins. Counting head ids must not collide with track section ids. Worth validating uniqueness across *both* namespaces at sheet load, alongside the duplicate-identifier check already needed.

**The simulator's `SlotDiscovery`** builds an `AxleSlot` for every identified group and expects Status / CATS / TL. Counting head groups have none of those — it already prints "has no Status group in its definition, it'll accept commands but nothing will actually change in the packet for it." It needs a second discovery path, and commands to drive direction pulses and speed values rather than axle counts.

**The message contract** — `YardMessage`, `CurrentMessage` and the `/zones` endpoint are all track-section-shaped. Counting head data needs somewhere to live, and clients need to be told which heads relate to which section.

### The relationship the sheet cannot currently express

A track section is bounded by counting heads. To use direction or speed meaningfully for a section, you have to know *which heads bound it*. Nothing in the current schema expresses that. It needs adding — probably as a pair of head references on each section row.

### The one that will catch you out

**The protocol tells you "direction 1" or "direction 2". It does not tell you "toward the buffer stop" or "away from it."**

Which physical direction each pulse corresponds to depends on how the wheel sensor was mounted, and it can be inverted at commissioning by a DIP switch on the AEB board — deliberately, so a sensor forced onto the opposite rail by mounting constraints needs no configuration changes anywhere else. Nothing in the packet tells you whether that inversion was applied.

So the mapping from *direction 1/2* to *into the yard / out of the yard* is a per-installation configuration fact that has to be captured in the sheet, per counting head, and verified on site. Get it backwards and the system will confidently report a departing rake as arriving.

---

## 1. Information on direction — 1 byte per counting head

Four of these bits are vital. `NED = 0` must drive a failsafe reaction (SRAC_FSE_012).

| Bit | Name | Meaning at 1 | Meaning at 0 | Vital |
| --- | --- | --- | --- | --- |
| 7 | `N1E1` | no traversing in direction 1 | traversing in direction 1, **or error** | yes |
| 6 | `N1E2` | no traversing in direction 2 | traversing in direction 2, **or error** | yes |
| 5 | `4E1` | traversing in direction 1 | no traversing in direction 1, **or error** | yes |
| 4 | `4E2` | traversing in direction 2 | no traversing in direction 2, **or error** | yes |
| 3 | `NED` | no error | error — direction not determinable, all pulses reset | yes |
| 2 | `CED` | communication error | no communication error | no |
| 1–0 | — | unused, always 0 | | |

Note the inverted sense on `N1E1`/`N1E2` — they are *inverted* 1-edge pulses, so the resting state is 1 and a traversal drives them to 0. Initial state with no traversing, no error: `4E1=4E2=0`, `CED=0`, `N1E1=N1E2=1`, `NED=1`.

Sheet rows:

```
CH_GROUP | Direction | <id> | group |  0 | 1 |
Direction | N1E1 |  | bit |  |  | 7
Direction | N1E2 |  | bit |  |  | 6
Direction | 4E1  |  | bit |  |  | 5
Direction | 4E2  |  | bit |  |  | 4
Direction | NED  |  | bit |  |  | 3
Direction | CED  |  | bit |  |  | 2
```

**Interpretation rule for the application layer:** only the combination of `4E1`, `4E2` and `NED` determines direction safely. `4E1=1, NED=1` → confirmed traversal in direction 1. `NED=0` → fault, ignore all pulses and go failsafe.

### Pulse timing — the part that will bite

These bits are **not instantaneous events.** Once a pulse fires it is *held*, and re-triggered by each subsequent axle. Build movement logic without accounting for this and you will read a stopped train as still moving.

**Hold duration.** A pulse is held for **2,550 ms plus a configurable direction pulse extension** — the base figure comes from the AEB, the extension is added by the COM-FSE and set in the *Timing FAdC* configuration word. Transmission delays mean the observed length can be somewhat shorter or longer than the sum of the two, so treat it as approximate rather than exact.

**Re-triggering.** If another axle arrives inside the hold window, the pulse extends rather than dropping and re-firing. During a moving rake the pulse therefore sits continuously high (or continuously low, for the inverted pair) across the whole train, then falls once the gap after the last axle exceeds the hold.

**The two pairs behave differently, in three ways:**

| | `4E1` / `4E2` | `N1E1` / `N1E2` |
| --- | --- | --- |
| Fires at | **end** of a traversal, once the second sensor system releases | **beginning** of a traversal, as soon as the first system is damped |
| Direction of change | 0 → 1 | 1 → 0 (inverted) |
| Re-triggered by | the next completed traversal | the next signal edge — four per traversal, so far more often |

That first row matters for a blind push. The 4-edge pulse only appears *after* a wheel has fully cleared the sensor, so it lags the physical event by the traversal time. The 1-edge pulse leads it. Neither is a "wheel is here right now" indicator.

**`NED` has a hold too.** After a fault clears, `NED` stays at 0 until the fault is rectified *and* the pulse length has expired, with the window starting from the moment of rectification. So a section will keep reporting a direction error for a couple of seconds after the underlying problem is fixed. Don't treat the delayed recovery as a second fault. One exception: after a communication fault between AEB and COM-FSE clears, only the configured extension applies, not the 2,550 ms base.

**Practical consequences:**

- A high pulse means *"a wheel passed within the hold window"*, not *"movement now"*. To detect a stop you need the pulse to fall, which takes at least the hold duration.
- Hold duration puts a floor on how quickly you can report a movement ending — roughly 2.5 seconds minimum, more with extension configured. If your display needs to react faster than that, direction pulses alone cannot deliver it and occupancy changes remain the faster signal.
- Find out the configured extension for your site before building anything on these bits. It changes the floor.
- On a slow blind push with wide axle spacing, gaps between axles can exceed the hold and the pulse will drop and re-fire mid-movement. Do not treat a falling pulse as proof the movement ended.

---

## 2. Information on speed — 5 bytes per counting head

| Offset | Field | Type | Length | Notes |
| --- | --- | --- | --- | --- |
| 0 | `RSR` | int | 1 | sensor type: 1 = RSR180, 3 = RSR123, 0 = none seen yet |
| 1 | `SP` | long | 4 | time between the two sensor systems responding, resolution 1/136,000,000 s |

```
CH_GROUP | Speed | <id> | group | 0 | 5 |
Speed | RSR |  | int  | 0 | 1 |
Speed | SP  |  | long | 1 | 4 |
```

**Derivation (compute server-side, don't push to clients):**

```
RSR180:  v [m/s] = (0.18  × k × 136_000_000) / SP     k = 0.8090778 (default, tunable)
RSR123:  v [m/s] = (0.131 × k × 136_000_000) / SP     k = 0.9808    (default, tunable)

km/h = v × 3.6
```

Guard `SP == 0` — it means no traversal measured yet, not infinite speed. Worked example from the document: RSR180 with SP = 713,024 → 27.7778 m/s → 100 km/h.

`k` should be configurable per counting head, since the document notes it can be optimised per application.

---

## 3. Information on wheel diameter — 5 bytes per counting head

| Offset | Field | Type | Length | Notes |
| --- | --- | --- | --- | --- |
| 0 | `RSR` | int | 1 | sensor type, as above |
| 1 | `XVAL` | int | 2 | |
| 3 | `YVAL` | int | 2 | `YVAL/XVAL` is proportional to diameter, always < 1 |

```
CH_GROUP | WheelDiameter | <id> | group | 0 | 5 |
WheelDiameter | RSR  |  | int | 0 | 1 |
WheelDiameter | XVAL |  | int | 1 | 2 |
WheelDiameter | YVAL |  | int | 3 | 2 |
```

**Derivation:** `d [mm] = YVAL × k / XVAL`, where `k` depends on sensor type *and* rail profile and must be determined per installation:

| Sensor | Rail profile | k (reference) |
| --- | --- | --- |
| RSR180 | S49 | 500 mm |
| RSR180 | UIC60 | 540 mm |
| RSR123 | S49 | 666 mm |
| RSR123 | UIC60 | 655 mm |

These are reference values only — re-determine on any change of sensor type or rail profile. Guard `XVAL == 0`.

---

## 4. I/O information of the AEB — 3 bytes per AEB

24 bits, `ACIO00`–`ACIO23`, vital. Byte 0 carries positions 23…16, byte 1 carries 15…8, byte 2 carries 7…0 — so read as one 24-bit big-endian word, `ACIOnn` sits at word bit `nn`.

```
AEB_IO | IO | <id> | group | 0 | 3 |
AEB_IO | ACIO23 |  | bit |  |  | 23
AEB_IO | ACIO22 |  | bit |  |  | 22
   ...
AEB_IO | ACIO00 |  | bit |  |  | 0
```

Failsafe status for these bits is 0. The AEB does not extend the pulse — the *external* system is responsible for holding a failsafe 0 long enough to guarantee transmission (SRAC_FSE_009).

---

## 5. Reverse direction — safety system → FAdC

Needed for Phase 3. Message must contain **only** these three types, in this order: Checkbyte, Reset information, safety-system I/O.

### 5.1 Checkbyte — 1 byte

| Bit | Name | Meaning |
| --- | --- | --- |
| 7 | `ILLB` | safety system loopback — value we send is reflected back as `ACLB` |
| 6–0 | — | unused, must be 0 |

### 5.2 Reset information — 1 byte per track section

Order must match the track-section order used in the FAdC → safety direction.

| Bit | Name | Reset type |
| --- | --- | --- |
| 7 | `PRST` | pre-reset |
| 6 | `RST` | reset |
| 5 | `DRST` | direct reset |
| 4 | `RRST` | restricted reset |
| 3 | `RSTR` | reset restriction cancelled |
| 2 | `PDRST` | preparatory direct reset |
| 1 | `PRRST` | preparatory restricted reset |
| 0 | — | unused, must be 0 |

All vital. **Only one bit per track section may be 1 at any moment.**

**Handshake sequence** (implement this as an explicit state machine, not fire-and-forget):

1. Precondition — for that track section: `RAC = RJO = RJT = 0` and `RAB = 1`.
2. Set the chosen reset bit to 1.
3. Wait for `RAC = 1` (accepted) or `RJO = 1` / `RJT = 1` (rejected operationally / technically). If neither appears within a few seconds, proceed anyway.
4. Set the reset bit back to 0.
5. Wait until `RAC`, `RJO` and `RJT` are all 0.

Multi-step resets (e.g. pre-reset then reset) run the whole sequence once per step.

### 5.3 I/O information of the safety system — 3 bytes

`ILIO00`–`ILIO23`, same bit layout as the AEB I/O in §4. Vital. Failsafe status 0, and the safety system must hold that 0 long enough to guarantee transmission through to the AEB (SRAC_FSE_010).

---

## 6. Message ordering

FAdC → safety system, per D21008 §3.2.1 — a contiguous series in this order, with only configured items present:

1. Checkbyte
2. Track section information — per AEB, per FMA: Status, then CATS, then TL
3. Counting head information — per AEB, per counting head: Direction, then Speed, then Wheel diameter
4. AEB / IO-EXB I/O information

Safety system → FAdC, per §3.2.2:

1. Checkbyte
2. Reset information, one byte per track section, **same section order as direction 1**
3. Safety-system I/O information

The existing sheet already reflects ordering 1–2 correctly. Extending it means appending groups in the order above, since offsets are positional.

---

## 7. Sizing ceilings

Useful for the configured-length check (FSE_SPEC70) and for load testing.

| Direction | Item | Max count | Bytes each |
| --- | --- | --- | --- |
| FAdC → safety | Checkbyte | 1 | 1 |
| | Status FMA 1 / FMA 2 | 40 each | 2 |
| | CATS | 80 | 2 |
| | TL | 80 | 2 |
| | Direction | 40 | 1 |
| | Speed | 40 | 5 |
| | Wheel diameter | 40 | 5 |
| | AEB I/O | 40 | 3 |
| Safety → FAdC | Checkbyte | 1 | 1 |
| | Reset FMA 1 / FMA 2 | 40 each | 1 |
| | Safety-system I/O | 1 | 3 |

Application data caps at **200 bytes excluding the checkbyte** (201 including) on the sending side, against the FSE protocol's own ceiling of 512. One COM-FSE evaluates up to 40 AEB boards. Minimum send interval and minimum processing interval are both **100 ms**.

Bandwidth: `bytes/s = 1000 × (83 + application_data_bytes) / interval_ms`, where 83 bytes is the combined Ethernet + IP + UDP + FSE overhead. Calculate per direction.
