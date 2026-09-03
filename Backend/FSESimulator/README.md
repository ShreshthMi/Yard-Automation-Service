# FSEsimulator

FSEsimulator is a standalone command-line tool for sending UDP packets to an FSE protocol receiver.

It uses `protocol_definition.xlsx` to understand the packet structure. Field offsets, identifiers, bit positions, and other protocol details are read from the Excel file at startup. Nothing is hard-coded in the simulator.

The simulator is a separate Maven project from the packet parser it is used to test.

- **Group ID:** `com.frauscher.fse`
- **Artifact ID:** `FSEsimulator`
- **Package:** `com.frauscher.fse.simulator`

## How it works

The simulator is split into small classes. Each class has one main responsibility.

| Class | Purpose |
|---|---|
| `Row` | Stores one row from an Excel sheet |
| `FieldNode` / `TreeBuilder` | Converts the flat Excel rows into a field tree |
| `ConfigLoader` | Reads `protocol_definition.xlsx` using Apache POI |
| `CoreLayout` | Finds the byte offset of top-level fields |
| `AxleSlot` | Stores the layout information for one track slot |
| `SlotDiscovery` | Finds track slots and their fields from the configuration |
| `Crc32` | Calculates CRC32 and CRC32 Inverse |
| `SimulatorState` | Stores axle counts and manually set status bits |
| `PacketBuilder` | Creates the UDP payload from the current simulator state |
| `Command` / `CommandParser` | Reads and validates commands entered by the user |
| `UdpSender` | Sends the payload using a UDP socket |
| `Simulator` | Runs the command-line interface |
| `Main` | Starts the application and loads the configuration |

The classes are kept simple and are connected directly through their constructors. There are no unnecessary interfaces, frameworks, or extra layers.

## What the simulator sends

The simulator sends only the application payload:

```text
Protocol Version
        ↓
      Fields
        ↓
     CRC32
        ↓
  CRC32 Inverse
```

It does **not** create Ethernet, IP, or UDP headers.

Those headers are created automatically by the operating system when the UDP packet is sent through the socket.

If the receiver expects a packet layout that includes Ethernet/IP/UDP header space before `Protocol Version`, the receiver must add the required padding before processing the payload. This is handled on the receiver side.

## Configuration comes from Excel

The simulator does not assume a fixed packet layout.

The following information is read from `protocol_definition.xlsx` when the application starts:

- Track identifiers
- Byte offsets
- Available status bits
- Fields available for each track
- Whether a track contains `CATS`
- Whether a track contains `TL`
- Whether a track contains `CLR`
- Whether a track contains `OCC`

This means the same simulator can work with different protocol definitions without changing the Java code.

If a field is not present for a particular track, it is simply ignored when the packet is built.

For example, a track without `CATS` or `TL` is still valid.

## Requirements

You need:

- JDK 17 or newer
- Maven

## Build

Run:

```bash
mvn package
```

The generated JAR file will be:

```text
target/FSEsimulator.jar
```

### Log4j message

You may see this message when starting the application:

```text
Log4j2 could not find a logging implementation...
Using SimpleLogger
```

This is harmless.

It comes from the `log4j-api` dependency used by Apache POI. The simulator does not require a separate Log4j implementation.

Do **not** add `log4j-core` just to remove this message. With the version of `log4j-api` bundled with POI 5.2.5, adding `log4j-core` can cause a `NoClassDefFoundError`.

There is a related comment in `pom.xml`.

## Run

Run the simulator with:

```bash
java -jar target/FSEsimulator.jar protocol_definition.xlsx 127.0.0.1 9000
```

The arguments are:

```text
java -jar <jar-file> <configuration-file> <receiver-IP> <receiver-port>
```

A sample `protocol_definition.xlsx` is included in the project. You can replace it with your own protocol definition.

## Commands

After starting the simulator, the command prompt looks like this:

```text
sim>
```

### Send axles

Add axles to a track:

```text
send <n> axle to <id>
```

Example:

```text
send 10 axle to 1T
```

### Remove axles

Remove a specific number of axles:

```text
remove <n> axle from <id>
```

Example:

```text
remove 4 axle from 1T
```

Remove all axles:

```text
remove all axle from <id>
```

Example:

```text
remove all axle from 1T
```

### Set a status bit

Set one status bit to `0` or `1`:

```text
send <BIT> bit as <0|1> for <id>
```

Example:

```text
send ERR bit as 1 for 1T
```

Multiple bits can be set in one command:

```text
send <BIT> as <0|1> and <BIT> as <0|1> for <id>
```

Example:

```text
send ERR bit as 1 and CE as 1 for 1T
```

### Run an axle cycle

Simulate a vehicle passing over a track:

```text
cycle <n> axle on <id> delay <ms>
```

Example:

```text
cycle 10 axle on 1T delay 100
```

This will:

1. Add the axles one at a time.
2. Wait the specified number of milliseconds between packets.
3. Remove the axles one at a time.
4. Return the track to its original axle count.

For `n` axles, the simulator sends `2n` packets. Each packet gets a new CRC.

The command runs synchronously, so the `sim>` prompt appears again only after the complete cycle finishes.

### Show status

```text
status
```

Shows the current axle counts and manually configured status bits.

### Show help

```text
help
```

Shows the available commands.

### Exit

```text
exit
```

or:

```text
quit
```

Stops the simulator.

## Command format

The simulator intentionally accepts only the command formats described above.

The following keywords are required where shown:

- `to`
- `from`
- `for`
- `on`
- `delay`

For example:

```text
send 10 axle to 1T
```

is valid, while a command with a different structure is rejected.

This keeps command handling predictable and avoids ambiguity.

## Track identifiers

The `<id>` value comes directly from the payload-definition sheet.

For example:

```text
1T
2T
3T
4T
```

These are only examples. If the Excel configuration contains different identifiers, the simulator automatically uses those identifiers instead.

The available identifiers are shown when the simulator starts and when `status` or `help` is used.

## Status bits

`<BIT>` can be any status bit defined in the Excel configuration for the selected identifier.

For example:

```text
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

The simulator does not have a fixed list of supported bits. If a new bit is added to the Excel configuration, it can be used without changing the Java code.

Multiple bits can be changed in one command using `and`:

```text
send ERR as 1 and CE as 1 for 1T
```

Bit overrides are kept separately for each identifier and remain active until they are changed.

### How bit values are selected

When a packet is created, the simulator determines each bit value in this order:

1. **Manual override** — if one was set, it is used.
2. **`CLR` / `OCC`** — if there is no manual override, these can be calculated from the current axle count.
3. **Default value** — if neither applies, the value is `0`.

This allows manual bit values to be used for testing while still keeping the normal automatic `CLR`/`OCC` behavior.
