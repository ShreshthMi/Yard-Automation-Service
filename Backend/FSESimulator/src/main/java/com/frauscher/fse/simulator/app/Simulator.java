package com.frauscher.fse.simulator.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.frauscher.fse.simulator.command.Command;
import com.frauscher.fse.simulator.command.CommandParser;
import com.frauscher.fse.simulator.model.AxleSlot;
import com.frauscher.fse.simulator.model.SlotDiscovery;
import com.frauscher.fse.simulator.net.UdpSender;
import com.frauscher.fse.simulator.packet.PacketBuilder;
import com.frauscher.fse.simulator.packet.SimulatorState;

/**
 * The interactive "sim>" command loop. Reads one line at a time, parses it with
 * {@link CommandParser}, validates identifiers/bit names against what
 * {@link SlotDiscovery} actually found in the sheet, and on any valid command
 * builds a fresh packet ({@link PacketBuilder}) and sends it
 * ({@link UdpSender}). All mutable state lives in {@link SimulatorState}.
 */
public class Simulator {

	private final Map<String, AxleSlot> axleSlots;
	private final SimulatorState state;
	private final PacketBuilder packetBuilder;
	private final UdpSender sender;
	private final CommandParser parser = new CommandParser();

	public Simulator(Map<String, AxleSlot> axleSlots, SimulatorState state, PacketBuilder packetBuilder,
			UdpSender sender) {
		this.axleSlots = axleSlots;
		this.state = state;
		this.packetBuilder = packetBuilder;
		this.sender = sender;
	}

	public void run() throws Exception {
		System.out.println("Packet simulator ready, sending to " + sender.destinationDescription());
		System.out.println("Identifiers found in payload-definition sheet: " + String.join(", ", axleSlots.keySet()));
		printHelp();

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String line;
		System.out.print("sim> ");
		while ((line = in.readLine()) != null) {
			handle(line.trim());
			System.out.print("sim> ");
		}
		sender.close();
	}

	private void handle(String line) {
		try {
			Command cmd = parser.parse(line);
			switch (cmd.type) {
			case EMPTY:
				break;
			case EXIT:
				sender.close();
				System.out.println("Bye.");
				System.exit(0);
				break;
			case HELP:
				printHelp();
				break;
			case STATUS:
				printStatus();
				break;
			case SEND_AXLE: {
				String id = requireIdentifier(cmd.identifier);
				int newCount = state.addAxles(id, cmd.n);
				sendAndReport(id, "axle count " + newCount + " (added " + cmd.n + ")");
				break;
			}
			case REMOVE_AXLE: {
				String id = requireIdentifier(cmd.identifier);
				int newCount = state.removeAxles(id, cmd.n);
				sendAndReport(id, "axle count " + newCount + " (removed " + cmd.n + ")");
				break;
			}
			case REMOVE_ALL_AXLE: {
				String id = requireIdentifier(cmd.identifier);
				state.removeAllAxles(id);
				sendAndReport(id, "axle count 0 (removed all)");
				break;
			}
			case SET_BITS: {
				handleSetBits(cmd);
				break;
			}
			case CYCLE: {
				handleCycle(cmd);
				break;
			}
			case SIMULATE_TRAIN: {
				handleSimulateTrain(cmd);
				break;
			}
			case UNRECOGNIZED:
			default:
				System.out.println("Unrecognized command. Type 'help' for the command list.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			System.out.println("Simulation interrupted, stopped early.");
		} catch (NumberFormatException e) {
			System.out.println(e.getMessage() != null ? e.getMessage() : "Expected a number. Type 'help' for usage.");
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			System.out.println("Failed to send packet: " + e.getMessage());
		}
	}

	private void handleSetBits(Command cmd) throws Exception {
		String id = requireIdentifier(cmd.identifier);
		AxleSlot slot = axleSlots.get(id);
		if (slot.statusOffset == null) {
			System.out.println(
					"Identifier \"" + id + "\" has no Status group defined - there are no bits to set for it.");
			return;
		}
		for (String bitName : cmd.bitUpdates.keySet()) {
			if (!slot.statusBits.containsKey(bitName)) {
				throw new IllegalArgumentException("Identifier \"" + id + "\" has no bit named \"" + bitName
						+ "\". Valid bits for it: " + String.join(", ", slot.statusBits.keySet()));
			}
		}
		state.setBits(id, cmd.bitUpdates);
		sendAndReport(id, "bits set -> " + cmd.bitUpdates);
	}

	/**
	 * Ramps an identifier's axle count up by n, one axle at a time, delayMs apart,
	 * then back down by n the same way - e.g. "cycle 10 axle on 1T delay 100" sends
	 * 1T's count up from its current value to current+10 one axle per packet (100ms
	 * apart), then back down to its original value the same way.
	 */
	private void handleCycle(Command cmd) throws Exception {
		String id = requireIdentifier(cmd.identifier);
		int n = cmd.n;
		long delayMs = cmd.delayMs;
		if (n <= 0)
			throw new IllegalArgumentException("Number of axles must be a positive number.");
		if (delayMs < 0)
			throw new IllegalArgumentException("Delay must be zero or a positive number of milliseconds.");

		System.out.println("Ramping " + id + " up by " + n + " axle(s), " + delayMs + "ms apart...");
		for (int i = 1; i <= n; i++) {
			int newCount = state.addAxles(id, 1);
			sender.send(packetBuilder.build(state));
			System.out.println("  [up " + i + "/" + n + "] " + id + " axle count now " + newCount);
			Thread.sleep(delayMs);
		}

		System.out.println("Ramping " + id + " down by " + n + " axle(s), " + delayMs + "ms apart...");
		for (int i = 1; i <= n; i++) {
			int newCount = state.removeAxles(id, 1);
			sender.send(packetBuilder.build(state));
			System.out.println("  [down " + i + "/" + n + "] " + id + " axle count now " + newCount);
			Thread.sleep(delayMs);
		}

		printStatus();
	}

	/**
	 * Simulates a train with a FIXED LENGTH physically moving through every
	 * discovered track section, in order - e.g. "simulate train length 20
	 * capacity 1T=15,2T=15,3T=3 delay 1T=100,2T=300,3T=600 pause 10000".
	 *
	 * This models a blind push: the locomotive is at the back, wagons at the
	 * front, so the driver can't see which zone the front is actually in - the
	 * whole point of the zone/CLR/warning system is to tell him. Since the
	 * train's length can exceed a single zone's capacity, the front and the tail
	 * are often in DIFFERENT zones at the same time - one zone draining while the
	 * next fills, simultaneously - not "one zone completes, then the next
	 * starts".
	 *
	 * The model: track sections are laid end to end into one long strip, each
	 * occupying [start, end) within it. The train's body at any moment is the
	 * interval [front - length, front). A zone's CATS is just how much of that
	 * interval currently overlaps that zone's own [start, end) span - computed
	 * fresh from a single "front position" every step, which is what makes
	 * several zones change at once fall out naturally, with no need to pace
	 * different zones on independent schedules.
	 *
	 * The front advances from 0 up to the total track length (so it stops
	 * exactly at the wall - it never moves further), pauses, then retreats back
	 * to 0 the same way, using each zone's own delay as the front passes through
	 * it (same delays as the way in).
	 *
	 * Depending on the numbers given, a zone can legitimately still be partially
	 * occupied even once the front is at the wall (if the train is longer than
	 * everything ahead of its tail), or fully clear well before the front
	 * reaches the last zone (if the train is short relative to the zones ahead)
	 * - both are correct physics for the given length/capacities, not bugs.
	 */
	private void handleSimulateTrain(Command cmd) throws Exception {
		if (cmd.trainLength == null || cmd.trainLength <= 0) {
			throw new IllegalArgumentException("Train length must be a positive number.");
		}
		long pauseMs = cmd.pauseMs;
		if (pauseMs < 0) {
			throw new IllegalArgumentException("Pause must be zero or a positive number of milliseconds.");
		}

		List<String> tracks = new ArrayList<>(axleSlots.keySet());
		Map<String, Integer> capacities = resolveCapacities(cmd, tracks);
		Map<String, Long> delays = resolveDelays(cmd, tracks);
		int trainLength = cmd.trainLength;

		int[] starts = new int[tracks.size()];
		int[] ends = new int[tracks.size()];
		int cumulative = 0;
		for (int i = 0; i < tracks.size(); i++) {
			starts[i] = cumulative;
			cumulative += capacities.get(tracks.get(i));
			ends[i] = cumulative;
		}
		int totalLength = cumulative;

		System.out.println("Simulating a blind push: train length " + trainLength + ", moving forward through: "
				+ String.join(" -> ", tracks) + " (total track length " + totalLength + ")");
		for (int pos = 1; pos <= totalLength; pos++) {
			applyTrainPosition(pos, trainLength, tracks, starts, ends);
			sender.send(packetBuilder.build(state));
			String zoneId = tracks.get(zoneIndexForStep(pos - 1, starts, ends));
			long delayMs = delays.get(zoneId);
			System.out.println("  front at " + pos + "/" + totalLength + " (entering " + zoneId + ", " + delayMs
					+ "ms apart) -> " + describeCounts(tracks));
			Thread.sleep(delayMs);
		}

		System.out.println("Train front at the wall. Pausing " + pauseMs + "ms before it starts leaving...");
		Thread.sleep(pauseMs);

		System.out.println("Simulating the train leaving the way it came...");
		for (int pos = totalLength - 1; pos >= 0; pos--) {
			applyTrainPosition(pos, trainLength, tracks, starts, ends);
			sender.send(packetBuilder.build(state));
			String zoneId = tracks.get(zoneIndexForStep(pos, starts, ends));
			long delayMs = delays.get(zoneId);
			System.out.println("  front at " + pos + "/" + totalLength + " (leaving via " + zoneId + ", " + delayMs
					+ "ms apart) -> " + describeCounts(tracks));
			Thread.sleep(delayMs);
		}

		System.out.println("Simulation complete.");
		printStatus();
	}

	/** Sets every zone's axle count to how much of the train's body (front-length, front] currently overlaps it. */
	private void applyTrainPosition(int frontPos, int trainLength, List<String> tracks, int[] starts, int[] ends) {
		int tailPos = frontPos - trainLength;
		for (int i = 0; i < tracks.size(); i++) {
			int overlapStart = Math.max(tailPos, starts[i]);
			int overlapEnd = Math.min(frontPos, ends[i]);
			int overlap = Math.max(0, overlapEnd - overlapStart);
			state.setAxleCount(tracks.get(i), overlap);
		}
	}

	/** Which zone contains the given 0-based position within the laid-out track strip. */
	private int zoneIndexForStep(int stepIndex, int[] starts, int[] ends) {
		for (int i = 0; i < starts.length; i++) {
			if (stepIndex >= starts[i] && stepIndex < ends[i]) {
				return i;
			}
		}
		return starts.length - 1;
	}

	private String describeCounts(List<String> tracks) {
		StringBuilder sb = new StringBuilder();
		for (String id : tracks) {
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(id).append('=').append(state.getAxleCount(id));
		}
		return sb.toString();
	}

	/**
	 * Resolves each known track's capacity: from cmd.perTrackCapacity if given
	 * (every known track must be covered), otherwise cmd.uniformCapacity applied
	 * to all of them.
	 */
	private Map<String, Integer> resolveCapacities(Command cmd, List<String> tracks) {
		Map<String, Integer> result = new LinkedHashMap<>();
		if (cmd.perTrackCapacity != null) {
			for (String id : tracks) {
				Integer cap = findCaseInsensitiveInt(cmd.perTrackCapacity, id);
				if (cap == null) {
					throw new IllegalArgumentException("No capacity given for identifier \"" + id
							+ "\". When using per-track capacities, every known identifier needs one: "
							+ String.join(", ", tracks));
				}
				if (cap <= 0) {
					throw new IllegalArgumentException("Capacity for \"" + id + "\" must be a positive number.");
				}
				result.put(id, cap);
			}
			warnAboutUnknownKeys(cmd.perTrackCapacity.keySet(), tracks, "capacity");
		} else {
			if (cmd.uniformCapacity == null || cmd.uniformCapacity <= 0) {
				throw new IllegalArgumentException("Capacity must be a positive number.");
			}
			for (String id : tracks) {
				result.put(id, cmd.uniformCapacity);
			}
		}
		return result;
	}

	/**
	 * Resolves each known track's delay: from cmd.perTrackDelayMs if given
	 * (every known track must be covered), otherwise cmd.delayMs applied to all
	 * of them.
	 */
	private Map<String, Long> resolveDelays(Command cmd, List<String> tracks) {
		Map<String, Long> result = new LinkedHashMap<>();
		if (cmd.perTrackDelayMs != null) {
			for (String id : tracks) {
				Long delay = findCaseInsensitiveLong(cmd.perTrackDelayMs, id);
				if (delay == null) {
					throw new IllegalArgumentException("No delay given for identifier \"" + id
							+ "\". When using per-track delays, every known identifier needs one: "
							+ String.join(", ", tracks));
				}
				if (delay < 0) {
					throw new IllegalArgumentException(
							"Delay for \"" + id + "\" must be zero or a positive number of milliseconds.");
				}
				result.put(id, delay);
			}
			warnAboutUnknownKeys(cmd.perTrackDelayMs.keySet(), tracks, "delay");
		} else {
			if (cmd.delayMs == null || cmd.delayMs < 0) {
				throw new IllegalArgumentException("Delay must be zero or a positive number of milliseconds.");
			}
			for (String id : tracks) {
				result.put(id, cmd.delayMs);
			}
		}
		return result;
	}

	private void warnAboutUnknownKeys(java.util.Set<String> typedIds, List<String> tracks, String label) {
		for (String typedId : typedIds) {
			boolean known = tracks.stream().anyMatch(t -> t.equalsIgnoreCase(typedId));
			if (!known) {
				System.out.println("Note: " + label + " given for \"" + typedId
						+ "\" but that's not a known identifier - ignored. Valid identifiers: "
						+ String.join(", ", tracks));
			}
		}
	}

	private Integer findCaseInsensitiveInt(Map<String, Integer> map, String id) {
		for (Map.Entry<String, Integer> e : map.entrySet()) {
			if (e.getKey().equalsIgnoreCase(id))
				return e.getValue();
		}
		return null;
	}

	private Long findCaseInsensitiveLong(Map<String, Long> map, String id) {
		for (Map.Entry<String, Long> e : map.entrySet()) {
			if (e.getKey().equalsIgnoreCase(id))
				return e.getValue();
		}
		return null;
	}

	private void sendAndReport(String id, String message) throws Exception {
		sender.send(packetBuilder.build(state));
		System.out.println("Sent: " + id + " " + message);
		printStatus();
	}

	private String requireIdentifier(String raw) {
		for (String known : axleSlots.keySet()) {
			if (known.equalsIgnoreCase(raw))
				return known;
		}
		throw new IllegalArgumentException(
				"Unknown identifier \"" + raw + "\". Valid identifiers: " + String.join(", ", axleSlots.keySet()));
	}

	private void printStatus() {
		System.out.println("Current state (per identifier, independent of each other):");
		for (String id : axleSlots.keySet()) {
			Map<String, Integer> overrides = state.getBitOverrides(id);
			String overrideText = overrides.isEmpty() ? "" : ("  bit overrides: " + overrides);
			System.out.println("  " + id + ": " + state.getAxleCount(id) + " axles" + overrideText);
		}
	}

	private void printHelp() {
		String firstId = axleSlots.keySet().iterator().next();
		System.out.println("Commands:");
		System.out.println("  send <n> axle to <id>              e.g. send 10 axle to " + firstId);
		System.out.println("  remove <n> axle from <id>          e.g. remove 4 axle from " + firstId);
		System.out.println("  remove all axle from <id>          e.g. remove all axle from " + firstId);
		System.out.println("  send <BIT> bit as <0|1> for <id>   e.g. send ERR bit as 1 for " + firstId);
		System.out.println("  send <BIT> as <0|1> and <BIT> as <0|1> for <id>");
		System.out.println("                                      e.g. send ERR bit as 1 and CE as 1 for " + firstId);
		System.out.println("  cycle <n> axle on <id> delay <ms>  e.g. cycle 10 axle on " + firstId + " delay 100");
		System.out.println(
				"                                      (adds n axles one at a time, ms apart, then removes them the same way)");
		System.out.println("  simulate train length <n> capacity <cap> delay <ms> [pause <ms>]");
		System.out.println("  simulate train length <n> capacity <id>=<cap>,... delay <id>=<ms>,... [pause <ms>]");
		System.out.println(
				"                                      e.g. simulate train length 20 capacity 1T=15,2T=15,3T=3 delay 1T=100,2T=300,3T=600 pause 10000");
		System.out.println("                                      Models a real train of fixed length physically moving through every");
		System.out.println("                                      discovered track (blind push - front and tail can be in different zones");
		System.out.println("                                      at once, so one zone can drain while the next fills, simultaneously),");
		System.out.println("                                      then leaves the same way using the same per-zone delays.");
		System.out.println("  status                              show current axle counts and bit overrides");
		System.out.println("  help                                show this message");
		System.out.println("  exit / quit                         stop the simulator");
		System.out.println("  <id> is one of: " + String.join(", ", axleSlots.keySet()));
	}
}