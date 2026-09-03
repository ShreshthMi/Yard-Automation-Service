# Test Strategy: YardAutomationService

Status: draft for sign-off. Written against the scope in
`YardAutomationService-analysis-v2.md` (rev 2) before any test code exists.

## 1. Context

YardAutomationService receives FSE protocol UDP packets from a COM-FSE101 (today,
from `FSESimulator` at 100 ms intervals), validates their CRCs, decodes them against
`protocol_definition.xlsx`, and broadcasts a per-yard status message over STOMP to a
driver-facing display. Its purpose is to help a driver pushing a rake from behind —
unable to see the leading wagon — judge how close the front is to the end of the track.

It is **listen-only**: it never transmits, commands, or resets.

Current deployment is an **internal test rig**: simulator plus service, no real COM-FSE
hardware, no real driver acting on the output. That bounds this strategy heavily, and
section 7 records what is deliberately deferred as a result. It does not change the fact
that the code's *destination* is a yard, so the correctness of the state model is treated
as the top priority even though today's blast radius is low.

The service has **no database, no message broker, and no persistent state**. All state is
one `AtomicReference<Snapshot>` (the loaded spreadsheet) and one `ConcurrentHashMap` of
audit checksums. This is a decode-and-interpret service with exactly one inbound I/O
boundary.

## 2. Risk model

Ranked by likelihood × blast radius. Every row traces to (a) a stated requirement in the
analysis, (b) a defect confirmed against code that exists, or (c) an operational concern
tied to the rig.

| # | Failure mode | Likelihood | Blast radius | Trace | Covered by |
|---|---|---|---|---|---|
| 1 | `CLR=0, OCC=0` (faulty) reported to the driver as **OCCUPIED** — the section reads as "wagons here" when the truth is "I cannot tell" | **H** — bug is present now | **H** | (a) §3 Group A, (b) `NotificationService:72` | Unit (state model), Integration (packet→message) |
| 2 | Audit trail records `OCCUPIED` for a faulty section — wrong state persisted, read months later at incident review when nobody can tell it was wrong | **H** — same root cause | **H** | (a) §9 Phase 1, (b) `TrackEventLogger:101` | Unit (status derivation), Integration (trail content) |
| 3 | Link goes silent; last message stays frozen on the driver's screen indefinitely, indistinguishable from live | **H** — no timer exists at all | **H** | (a) §6 | Unit (timeout state machine, fake clock), Integration (stale broadcast) |
| 4 | `CE=1` (AEB unreachable) misreported as `FAULTY` because the truth table is checked before `CE` — every other bit is 0 under `CE`, so the two look identical | **M** | **H** | (a) §3 Group B, §4 ordering | Unit (state model, ordering) |
| 5 | Under-length / garbage datagram **accepted**, decodes to empty payload, flips every yard into an error state | **M** | **M** | (b) `PacketValidator:29`, **reproduced** | Unit (validator), Integration (rejection path) |
| 6 | Duplicate identifier in the sheet collapses to a `List`, `extractClr` returns null, section silently reports **not occupied** — a silent failure in the occupancy path with no warning anywhere | **L** — needs a sheet edit | **H** — silent | (b) `NotificationService:108` | Unit (load-time validation) |
| 7 | `CATS`/`TL` decoded unsigned; D21008 specifies signed — a negative train length reads as ~65,000 | **M** | **M** | (a) §8 closing note | Unit (decoder) |
| 8 | Blank Offset cell in the core sheet → NPE at packet time rather than a clear error at load time | **L** | **M** | (b) `CoreLayout:17` | Unit (load-time validation) |
| 9 | The "row is being SKIPPED" warning fires on every load for the sheet's trailing note row. Warning fatigue on the one diagnostic guarding a genuinely dangerous condition — and it goes to `System.err`, so it never reaches a log file | **H** — fires every load today | **M** | (b) confirmed by probe | Unit (loader diagnostics) |
| 10 | Hot reload swaps the snapshot mid-packet; a reader sees a mix of old and new | **L** — design looks sound | **M** | (c) `ProtocolRegistry` | Unit (concurrent reload) |
| 11 | Decode, CSV write and broadcast all run inline on the UDP receive thread; a slow client applies backpressure to the socket buffer and drops are invisible — no counter, no metric | **M** | **M** | (b) `UdpListener:68` | Deferred — see §7 |

Rows 1–4 are the product. If the suite only ever covers those four, it has earned its
keep.

**Deliberately excluded from this table** (would be speculative for a test rig):
cache invalidation (no cache), schema drift (no database), transaction rollback (no
transactions), auth bypass (see §7).

## 3. Layer allocation

**Override to a pyramid, not the testing-trophy default.** The trophy exists because most
backend bugs live at integration boundaries with real databases and brokers. This service
has neither. Its risk is concentrated in **pure functions over bytes and rows** — CRC,
tree building, offset resolution, bit extraction, state derivation. Those are cheap,
fast, and exhaustively testable in-process. Spinning up containers would buy nothing here.

The one genuine boundary — UDP in, STOMP out — gets a thin end-to-end layer rather than a
thick one.

Because CI is **local only for now**, the whole suite must stay fast enough that people
actually run it. Budget: **under 20 seconds**, no container pulls, no network.

| Layer | Covers | Does not cover | Tooling | Target | Budget |
|---|---|---|---|---|---|
| **Static** | Type safety; the compiler already catches most row/field misuse | Semantic errors in the sheet | `javac`, existing Maven build | — | free |
| **Unit** (thick) | Risks 1–4, 6–10. `Crc32`, `CoreLayout`, `TreeBuilder`, `PacketDecoder`, `ZoneColors`, state derivation, `TrackEventLogger` change detection, loader diagnostics | Whether the pieces compose correctly | JUnit 5 + AssertJ (via `spring-boot-starter-test`, already declared and unused) | ~60–80 tests | < 5 s |
| **Integration** (in-process, no Spring context) | Full pipeline: real `protocol_definition.xlsx` → synthesized packet bytes → validator → decoder → `NotificationService` → captured `YardMessage`. Risks 1, 2, 5 end to end | Real sockets, real STOMP | JUnit 5, real sheet, fake broadcaster | ~15–25 tests | < 5 s |
| **Contract** | The `YardMessage` / `CurrentMessage` JSON shape that `Blind-push-demo.html` (and any future client) depends on | Client rendering | Jackson serialization + golden JSON | ~5 tests | < 1 s |
| **E2E** | One path through the real Spring context: UDP datagram in on an ephemeral port → STOMP message out | Browser, network partitions | `@SpringBootTest`, real `DatagramSocket` | 2–3 tests | < 10 s |
| **Non-functional** | Deliberately minimal — see §5 and §7 | Load, soak, chaos | — | — | — |

## 4. Mock posture

Very little needs mocking, which is a good sign for testability.

| Collaborator | Mocked? | Why | Risk if reality diverges | Payback |
|---|---|---|---|---|
| `YardBroadcaster` / `SimpMessagingTemplate` | **Captured, not mocked** — a recording fake that stores messages | Assertions need the message, not the transport | Low: the real class is a 3-line delegation | The E2E layer exercises the real one |
| `Clock` (for link timeout, §6) | **Injected fake** | Timeout logic cannot be tested against wall clock without sleeps | The fake could drift from `Instant.now()` semantics | Inject `java.time.Clock`, not a custom abstraction — same type in prod and test |
| UDP socket | **Not mocked** at unit level — tests call the pipeline directly with byte arrays | The socket adds nothing to decode correctness | Framing/padding bugs live in `UdpListener`, not the pipeline | E2E layer uses a real socket |
| `protocol_definition.xlsx` | **Not mocked** — the real committed file is the fixture | It *is* the contract between simulator and service | — | Programmatic `List<Row>` fixtures cover edge cases the real sheet can't express |
| POI / `ConfigLoader` | **Not mocked** | Generating a real `.xlsx` in-test with POI is easy and tests the actual parser | — | — |

No mock in this suite currently lacks a payback plan.

## 5. Non-functional concerns

- **Concurrency.** `ProtocolRegistry.reload` is `synchronized` and swaps one
  `AtomicReference`; `UdpListener` re-reads the snapshot per packet. The design is sound
  on inspection — one test should prove a reader never observes a torn snapshot under a
  concurrent reload (risk 10). `TrackEventLogger`'s `ConcurrentHashMap` is written from a
  single thread today; a test should pin that assumption so it fails loudly if a second
  ingest thread is ever added.
- **Idempotency.** Not applicable in the usual sense — each packet is a fresh state
  observation, not a command. The one idempotence-shaped property that *does* matter:
  replaying an identical packet must produce no new audit line (that is
  `TrackEventLogger`'s entire purpose). Worth an explicit test.
- **Timeouts and retries.** No outbound calls, so nothing to time out. The inbound
  timeout (risk 3) is the whole of §6 and is the single largest missing behaviour.
- **Clock.** `TrackEventLogger` calls `LocalDateTime.now()` directly, and the coming
  link-timeout work needs time. Inject `java.time.Clock` before writing timeout tests, so
  no test ever sleeps.
- **Resource exhaustion.** Rejected-packet logging is unbounded (defect 6). Out of scope
  for a rig — see §7 — but the disk guard belongs before any real deployment.
- **Backpressure.** Risk 11. Real, but unobservable today because there is no metric to
  assert on. Testing it requires building the metric first; that is a Phase 2 change, not
  a test.

## 6. Test data strategy

- **Anchor fixture:** the real `Backend/protocol_definition.xlsx`, committed. It is the
  shared contract between simulator and service, so tests must load the same file the
  running system does.
- **Edge-case fixtures:** built programmatically as `List<Row>`. Every parser entry point
  (`TreeBuilder.build`, `PacketDecoder.buildFullTree`, `CoreLayout.findOffset`,
  `ProtocolRegistry.Snapshot`) takes rows rather than a file path, so cases that cannot be
  expressed in the real sheet — duplicate identifiers, blank offsets, malformed types —
  need no `.xlsx` at all.
- **Generated sheets:** for `ConfigLoader` itself, build a small `.xlsx` in-test with POI.
  Covers the loader, `YardDiscovery` and the warning diagnostics without shipping a
  fixture zoo.
- **Packet bytes:** a `TestPacketFactory` helper that writes fields at sheet-derived
  offsets and computes real CRCs. It deliberately mirrors `FSESimulator`'s `PacketBuilder`
  rather than importing it (separate Maven module today). **Flagged as debt:** once
  `fse-protocol-common` is extracted per the file reference §6, this helper should move
  there so a third copy of the wire format cannot drift.
- **Determinism.** No random values, no wall clock in assertions, no `Thread.sleep`
  anywhere. Timestamps come from an injected fixed `Clock`.
- **PII / production data:** none exists — the payload is axle counts and status bits.

## 7. Out of scope

Bounded by "internal test rig only". Each of these returns to scope before any deployment
where a driver acts on the output.

- **Authentication and origin lockdown** (defects 7, 8). `origins="*"` everywhere and an
  unauthenticated `POST /api/protocol/reload` accepting an arbitrary filesystem path are
  real and serious — but on a closed rig there is no attacker, and testing auth that
  doesn't exist yet tests nothing. **Must be built and tested before site deployment.**
- **Load, soak and chaos testing.** No SLO exists to test against. 100 ms packets from one
  simulator is not a load problem.
- **Rejected-packet log disk guard** (defect 6). A flood needs a hostile sender; the rig
  has one cooperative one.
- **Performance of the double tree walk** (defect 3). Real at the spec ceiling of 40 AEBs;
  irrelevant at three sections. Revisit when the sheet grows.
- **Backpressure metrics** (risk 11). Requires building instrumentation first.
- **The four missing application-data types** — direction, speed, wheel diameter, AEB I/O.
  Phase 5. Not in the code, nothing to test.
- **Reverse-direction / reset transmission.** Explicitly out of the listen-only scope.
- **Frontend behaviour.** `Blind-push-demo.html`'s own fault handling is out of scope as
  code, but §3's finding stands: that logic belongs server-side, and moving it there is
  what the contract layer pins.
- **`ProtocolReloadController` as a feature.** Nothing in the scope document asks for
  runtime reload. It is tested only insofar as it must not corrupt a running snapshot;
  whether it should exist at all is a product question.

## 8. CI gating

CI is **local only for now** — a deliberate, stated choice, so this section describes the
target rather than the present.

- **Today:** `mvn test` on a developer machine. The suite's < 20 s budget exists precisely
  so this happens without friction.
- **Gap worth naming:** `.gitlab-ci.yml` runs `build_backend` straight into kaniko and
  `deploy_backend` straight into an Azure webhook on `main`, with **no test stage between
  them**. Nothing currently stops a red build from deploying. Adding a `test` stage before
  `build` is a small change and the single highest-value CI improvement available.
- **When CI arrives:** unit + integration + contract block merge; E2E runs on `main`.
- **Flakiness policy:** zero tolerance in a suite this size and this fast. A flaky test
  here means a real race, not a slow runner — investigate rather than quarantine.
- **Coverage signal:** branch coverage on changed lines. Not an absolute line target.

## 9. Anti-goals

- **We will not chase a line-coverage number.** Risks 1–4 covered well beats 90% covered
  shallowly. `Simulator.java` is the largest file in the package and warrants almost no
  tests, because it is a developer tool whose failures are immediately visible.
- **We will not mock the spreadsheet.** It is the contract between the two projects;
  mocking it would test a fiction.
- **We will not assert on private methods or internal call sequences.** Tests assert on
  the resolved `YardMessage` and the audit line — the two things the outside world sees.
- **We will not add Testcontainers.** There is no database and no broker. Containers would
  add 30 seconds and cover nothing.
- **We will not write tests that sleep.** The link-timeout work makes this tempting;
  inject a `Clock` instead.
- **We will not pin current behaviour where current behaviour is wrong.** Risks 1, 2 and 4
  are bugs. Their tests assert the *correct* state model from §4 and are expected to fail
  until Phase 1 lands. Characterization tests are for `Crc32` and the decoder, where
  behaviour is believed correct and must not drift.

## 10. A note on CRC verification

The analysis states `Crc32` was verified by hand against D3487 Appendix C at six index
points. That document is not in this repository, so tests written here can only
**characterize** current behaviour — they lock it against drift, they do not independently
prove it correct. If the Appendix C vectors can be extracted from D3487, they should
replace the characterization values and become the real conformance test. Until then the
distinction should not be blurred.
