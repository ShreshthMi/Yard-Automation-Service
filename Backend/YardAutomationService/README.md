# YardAutomationService

YardAutomationService is a Spring Boot application that receives FSE protocol UDP packets, validates and decodes them, and sends yard status updates to connected clients through WebSocket.

The packet structure is read from `protocol_definition.xlsx`. Field offsets, identifiers, status bits, and yard/track configuration are not hard-coded in the application.

The service can be used together with the `FSEsimulator` project for testing.

## How it works

The application processes each UDP packet in the following steps:

1. **Receive** the UDP packet.
2. **Validate** the CRC32 and CRC32 Inverse values.
3. **Decode** the packet using the structure from `protocol_definition.xlsx`.
4. **Find** the track sections belonging to each yard defined in the sheet.
5. **Create** a message for each yard.
6. **Send** the yard message through WebSocket.

Invalid packets are rejected and written to `rejected-packets.log`.

The protocol definition can be reloaded at runtime, without restarting the service. See [Reloading the protocol definition](#reloading-the-protocol-definition).

## Project structure

```text
com.frauscher
│
├── YardAutomationServiceApplication.java
│   Application entry point
│
├── protocol/
│   Reads the protocol definition and decodes packets
│   ├── Row
│   ├── FieldNode
│   ├── TreeBuilder
│   ├── ConfigLoader
│   ├── CoreLayout
│   ├── Crc32
│   ├── PacketDecoder
│   └── ProtocolRegistry
│
├── ingest/
│   Receives and validates UDP packets
│   ├── UdpListener
│   ├── PacketValidator
│   ├── ValidationResult
│   └── RejectedPacketLogger
│
├── yard/
│   Yard and track configuration, derived from the protocol definition
│   ├── AppProperties
│   ├── YardConfig
│   ├── TrackSectionConfig
│   ├── YardDiscovery
│   └── ZoneColors
│
├── notification/
│   Creates messages for each yard
│   ├── NotificationService
│   ├── CurrentMessage
│   └── YardMessage
│
├── websocket/
│   Sends messages to WebSocket clients
│   ├── WebSocketConfig
│   └── YardBroadcaster
│
├── api/
│   Read-only endpoints used by frontend clients
│   ├── YardZonesController
│   └── ProtocolReloadController
│
└── audit/
    Writes the track-event audit trail (see below)
    └── TrackEventLogger
```

Each part has a specific responsibility:

- `protocol` handles the FSE packet structure and holds the currently-loaded definition.
- `ingest` receives and validates packets.
- `yard` derives yard/track configuration from the protocol definition.
- `notification` creates yard messages.
- `websocket` sends messages to connected clients.
- `api` exposes the yard list, zone metadata, and the reload endpoint over HTTP.
- `audit` writes the track-event CSV trail.

## Packet processing

### 1. Receive the packet

`UdpListener` listens on the configured UDP port.

The UDP payload contains the FSE application data. Ethernet, IP, and UDP headers are added by the operating system and are not part of the application payload.

The protocol definition may contain offsets that assume space for these headers. The listener therefore adds the required zero-byte padding before `Protocol Version` so that the offsets from the Excel configuration remain valid.

### 2. Validate the packet

`PacketValidator` checks:

- CRC32
- CRC32 Inverse

If either value is incorrect, the packet is rejected.

Rejected packets are written to:

```text
rejected-packets.log
```

The packet is not processed any further.

### 3. Decode the packet

`PacketDecoder` uses the structure from `protocol_definition.xlsx` to convert the packet into a data tree.

The decoder does not use a fixed list of fields. It uses whatever fields and status bits are defined in the Excel configuration.

### 4. Create yard messages

`NotificationService` checks every yard defined in `protocol_definition.xlsx` (see [Yard configuration](#yard-configuration)).

For each yard, it finds the configured track sections in the decoded packet using their identifiers, then determines the current message for that yard: the message and warning belonging to the most advanced occupied track section, or nothing if the yard is clear.

If a configured identifier cannot be found in the decoded packet at all, the yard's message is replaced with an error instead of guessing - see `CurrentMessage`.

One UDP packet can therefore generate messages for multiple yards.

### 5. Send the yard message

`YardBroadcaster` sends each yard's message to its own WebSocket topic.

For example:

```text
/topic/yard/Test Yard 1
```

A client subscribed to a yard's topic receives only that yard's messages.

## Example WebSocket message

A yard message looks like this:

```json
{
  "yardName": "Test Yard 1",
  "payload": {
    "checkByte": { "ACLB": 0, "SR": 0, "A1": 0 },
    "aebFma": [
      {
        "1T": {
          "status": { "CLR": 0, "OCC": 1, "NZ": 0, "RR": 0, "RAB": 0, "WCT": 0, "ERR": 0, "PT": 0, "CE": 0, "RAC": 0, "RJO": 0, "RJT": 0 },
          "CATS": 3,
          "TL": 30
        },
        "2T": {
          "status": { "CLR": 1, "OCC": 0, "NZ": 0, "RR": 0, "RAB": 0, "WCT": 0, "ERR": 0, "PT": 0, "CE": 0, "RAC": 0, "RJO": 0, "RJT": 0 },
          "CATS": 0,
          "TL": 0
        }
      }
    ]
  },
  "currentMessage": {
    "message": "ZONE 1 Occupied",
    "warning": "Reduce Speed to 15 KM/hr",
    "error": null,
    "color": "#639922"
  }
}
```

`payload` is the decoded packet's payload section, forwarded as-is - every identifier the sheet defines, whether it belongs to a yard or not. `currentMessage` is precomputed server-side so every client shows the same thing without re-deriving it from `payload` itself. `color` follows a fixed rule based on how close the occupied section is to the end of the yard - see [Zone colors](#zone-colors).

If a configured identifier is missing from the packet, `currentMessage` looks like this instead:

```json
{
  "message": null,
  "warning": null,
  "error": "Identifier(s) not found in this packet: 2T",
  "color": null
}
```

## Yard configuration

Yards and their track sections are defined directly in `protocol_definition.xlsx`, on the payload-definition sheet, using three additional columns on identifier rows:

| Column | Meaning |
| --- | --- |
| Yard Name | Which yard this identifier's track section belongs to |
| Occupied Message 1 | Message shown when this identifier is occupied (CLR = 0) |
| Occupied Message 2 | Warning shown when this identifier is occupied (CLR = 0) |

For example:

| Parent | Field | Identifier | Type | Offset | Length | Bit | Yard Name | Occupied Message 1 | Occupied Message 2 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AEB_FMA | FMA | 1T | group | 0 | 6 | | Test Yard 1 | ZONE 1 Occupied | Reduce Speed to 10 KM/hr |
| AEB_FMA | FMA | 2T | group | 6 | 6 | | Test Yard 1 | ZONE 2 Occupied | Reduce Speed to 5 KM/hr |
| AEB_FMA | FMA | 3T | group | 0 | 6 | | Test Yard 1 | ZONE 3 Occupied | Apply Brakes, Stop Immediately! |

Only identifier rows need these three columns filled in. Every other row - Status, bit rows, CATS, TL, and so on - leaves them blank.

Leave Yard Name blank on an identifier that shouldn't be part of any yard. It still decodes normally; it's just not surfaced by `NotificationService` or the yard API endpoints.

Track sections belong to a yard in the order their rows appear in the sheet - the first identifier row with a given Yard Name is that yard's first (innermost) section, and so on toward the boundary. This is also the order used for zone numbering and zone colors.

A yard has no separate id - its name from the sheet is used everywhere: as the WebSocket topic name, the REST API's `yardId` path segment, and `yardName` in the WebSocket message.

This replaces the `app.yards[...]` properties that used to configure yards separately. Keeping the packet layout and the yard configuration in one file means they can't drift out of step with each other.

## Zone colors

`ZoneColors` assigns each track section a color purely from its position within its yard, not from configuration:

- The last section (closest to the boundary): red
- The section before it: single yellow
- The section before that: double yellow
- Anything earlier: green

This applies to any number of track sections - a yard with only two sections gets single yellow and red; a yard with five gets green, green, double yellow, single yellow, red.

## Application configuration

`app.protocol-definition-path` and `app.udp.port` both have working defaults in `src/main/resources/application.properties`, so the application runs with no external properties file at all:

```properties
app.protocol-definition-path=protocol_definition.xlsx
app.udp.port=9000
```

Override either one at the command line:

```bash
java -jar target/YardAutomationService.jar --app.protocol-definition-path="C:/projects/ParserConfigs/protocol_definition.xlsx" --app.udp.port=9001
```

There is no separate properties file for this - command-line arguments are the only override mechanism this project uses. The path can be relative to the directory the application is started from, or an absolute path.

## Reloading the protocol definition

The protocol definition can be reloaded while the application is running, without a restart:

```text
GET  /api/protocol/current   - what's loaded right now (path, core row count, yard count)
POST /api/protocol/reload    - reload; body { "path": "..." } is optional, defaults to re-reading the current path
```

Example, after editing `protocol_definition.xlsx` in place:

```bash
curl -X POST http://localhost:8080/api/protocol/reload
```

A failed reload (bad path, malformed sheet) returns `400` with the reason and leaves whatever was already loaded, and running, untouched. Yard configuration, packet decoding, and CRC validation all read from the same reloaded snapshot, so a reload takes effect on the very next packet.

## Build

Requires:

- JDK 17 or newer
- Maven

Build the application using:

```bash
mvn clean package
```

The generated JAR file will be:

```text
target/YardAutomationService.jar
```

### Log4j message

You may see this message when starting the application:

```text
Log4j2 could not find a logging implementation...
Using SimpleLogger
```

This is harmless and comes from Apache POI's logging dependencies.

Do **not** add `log4j-core` just to remove this message. With the version of `log4j-api` used by POI 5.2.5, adding `log4j-core` can cause a `NoClassDefFoundError`.

## Run

Start the application with:

```bash
java -jar target/YardAutomationService.jar
```

This uses the bundled defaults (`protocol_definition.xlsx` in the working directory, port 9000). To point at files elsewhere:

```bash
java -jar target/YardAutomationService.jar --app.protocol-definition-path="C:/projects/ParserConfigs/protocol_definition.xlsx"
```

The project contains a sample `protocol_definition.xlsx` you can run the application against directly from the project root.

## Testing with FSEsimulator

The service can be tested using the `FSEsimulator` project.

Start `YardAutomationService` first and make sure it is listening on the required UDP port.

Then start the simulator:

```bash
java -jar target/FSEsimulator.jar "C:/projects/ParserConfigs/protocol_definition.xlsx" 127.0.0.1 9000
```

Use commands such as:

```text
send 10 axle to 1T
remove 4 axle from 1T
cycle 10 axle on 1T delay 100
simulate train length 20 capacity 1T=15,2T=15,3T=3 delay 1T=100,2T=300,3T=600 pause 10000
```

The simulator sends UDP packets to the receiver.

The receiver then:

```text
FSEsimulator
     │
     │ UDP
     ▼
YardAutomationService
     │
     ├── Validate CRC
     ├── Decode packet
     ├── Find yard tracks
     └── Create yard message
             │
             │ WebSocket
             ▼
        WebSocket Client
```

## Testing WebSocket output

The service uses STOMP over SockJS.

A simple browser client can connect as follows:

```html
<script src="https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js"></script>

<script>
  const socket = new SockJS('http://localhost:8080/ws');
  const client = Stomp.over(socket);

  client.connect({}, () => {
    client.subscribe('/topic/yard/Test Yard 1', (msg) => {
      console.log(JSON.parse(msg.body));
    });
  });
</script>
```

The WebSocket endpoint is:

```text
/ws
```

Yard messages are published under:

```text
/topic/yard/{yardName}
```

A client subscribed to a yard receives only messages for that yard.

A full-featured browser test page (`yard-test-viewer.html`) is included separately - a live track diagram, per-zone axle counts, and an audible last-zone alarm, all driven by these same endpoints.

## Rejected packets

Rejected packets are written to:

```text
rejected-packets.log
```

in the directory configured by `app.audit-log-path` (see [Track-event audit trail](#track-event-audit-trail) below - both logs share the same directory setting).

The log contains information such as:

- Packet size
- CRC error
- Received CRC value
- Calculated CRC value
- Packet contents in hexadecimal

The rejected-packet log is separate from the normal application console log and is rotated daily into `rejected-packets.YYYY-MM-DD.log`.

## Track-event audit trail

Every occupied/clear transition, and every ERR/CE active/cleared transition, is written to a daily CSV file. Each day's file is self-dated from the moment it's created:

```text
track-events-2026-08-13.csv
```

A line is written whenever a track's `(status, CATS)` combination actually changes - never once per packet, and never a repeat of the exact same combination. This means every distinct `CATS` value gets its own line while a track stays occupied (1, 2, 3, ... as axles arrive), not just the moment it first became occupied. A track already occupied (or already in error) the very first time this service observes it is logged as a starting state, not silently skipped - but the ordinary default state (clear, no error) isn't logged just because the service happened to start watching then.

Columns (the file itself has no header row, deliberately - see note below):

```text
dateTime,yardName,trackName,status,CATS
```

`CATS` is that identifier's axle count at the moment of the event (blank if the identifier has no CATS field, e.g. a status-only track section).

`status` is one value per track, not several independent ones - if occupancy and an error are both true at the same moment, `ERR` takes priority over `CE`, which takes priority over `OCCUPIED`/`CLEAR`:

```text
ERR         ERR bit is 1
CE          CE bit is 1 (communication error, distinct from ERR) and ERR is not active
OCCUPIED    CLR = 0 and neither ERR nor CE is active
CLEAR       CLR = 1 and neither ERR nor CE is active
```

No header row is written to the file. A header would need to come from somewhere that only runs once per calendar day, but this service can restart multiple times in one day during normal use - writing the header at startup would print it again after every restart, scattered through the middle of that day's file. The column order above is fixed and only documented here.

The directory - shared with `rejected-packets.log` - defaults to the working directory and is configured with:

```properties
app.audit-log-path=.
```

Override it at the command line, same as the other `app.*` properties:

```bash
java -jar target/YardAutomationService.jar --app.audit-log-path="C:/logs/yard-events"
```

Both audit logs are separate from the normal application console log.

## Configuration-driven status bits

The track status is not defined by a fixed Java DTO.

The available status bits come from `protocol_definition.xlsx`.

For example, a track may contain:

```text
CLR
OCC
ERR
CE
RAC
RJO
RJT
PT
WCT
RAB
RR
NZ
```

If the protocol definition changes or a new status bit is added, the receiver can use it without requiring a Java code change.

This keeps the packet parser, simulator, and receiver consistent with the same protocol definition.