package com.frauscher.fse.simulator.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.frauscher.fse.simulator.app.Simulator;

/**
 * Parses one raw command line into a {@link Command}. Pure parsing only - no
 * identifier validation (that needs the live set of known identifiers, which
 * belongs to {@link Simulator}) and no side effects.
 *
 * Recognized syntax: exit | quit help status send <n> axle to <id> remove <n>
 * axle from <id> remove all axle from <id> send <BIT> bit as <0|1> [and <BIT>
 * as <0|1> ...] for <id> cycle <n> axle on <id> delay <ms> simulate train
 * length <n> capacity <cap>|<id>=<cap>[,<id>=<cap>...] delay
 * <ms>|<id>=<ms>[,<id>=<ms>...] [pause <ms>]
 */
public class CommandParser {

	private static final Pattern SEND_BITS_PATTERN = Pattern.compile("^send\\s+(.+)\\s+for\\s+(\\S+)$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern BIT_ASSIGNMENT_PATTERN = Pattern.compile("^(\\w+)\\s+(?:bit\\s+)?as\\s+([01])$",
			Pattern.CASE_INSENSITIVE);

	// Default pause between "train fully occupies the yard, front at the wall" and
	// "train starts leaving", used when "simulate train ..." is given without an
	// explicit "pause <ms>" clause.
	private static final long DEFAULT_SIMULATE_TRAIN_PAUSE_MS = 3000L;

	public Command parse(String line) {
		if (line.isEmpty())
			return Command.of(Command.Type.EMPTY);

		String[] parts = line.split("\\s+");
		String cmd = parts[0].toLowerCase();

		if (cmd.equals("exit") || cmd.equals("quit")) {
			return Command.of(Command.Type.EXIT);
		}
		if (cmd.equals("help")) {
			return Command.of(Command.Type.HELP);
		}
		if (cmd.equals("status")) {
			return Command.of(Command.Type.STATUS);
		}
		if (cmd.equals("send") && parts.length == 5 && parts[2].equalsIgnoreCase("axle")
				&& parts[3].equalsIgnoreCase("to")) {
			return Command.axle(Command.Type.SEND_AXLE, parseInt(parts[1]), parts[4]);
		}
		if (cmd.equals("remove") && parts.length == 5 && parts[1].equalsIgnoreCase("all")
				&& parts[2].equalsIgnoreCase("axle") && parts[3].equalsIgnoreCase("from")) {
			return Command.removeAll(parts[4]);
		}
		if (cmd.equals("remove") && parts.length == 5 && parts[2].equalsIgnoreCase("axle")
				&& parts[3].equalsIgnoreCase("from")) {
			return Command.axle(Command.Type.REMOVE_AXLE, parseInt(parts[1]), parts[4]);
		}
		if (cmd.equals("cycle") && parts.length == 7 && parts[2].equalsIgnoreCase("axle")
				&& parts[3].equalsIgnoreCase("on") && parts[5].equalsIgnoreCase("delay")) {
			return Command.cycle(parseInt(parts[1]), parts[4], Long.parseLong(parts[6]));
		}
		if (cmd.equals("simulate") && parts.length == 10 && parts[1].equalsIgnoreCase("train")
				&& parts[2].equalsIgnoreCase("length") && parts[4].equalsIgnoreCase("capacity")
				&& parts[6].equalsIgnoreCase("delay") && parts[8].equalsIgnoreCase("pause")) {
			return parseSimulateTrain(parts[3], parts[5], parts[7], parts[9]);
		}
		if (cmd.equals("simulate") && parts.length == 8 && parts[1].equalsIgnoreCase("train")
				&& parts[2].equalsIgnoreCase("length") && parts[4].equalsIgnoreCase("capacity")
				&& parts[6].equalsIgnoreCase("delay")) {
			return parseSimulateTrain(parts[3], parts[5], parts[7], null);
		}
		if (cmd.equals("send")) {
			Matcher m = SEND_BITS_PATTERN.matcher(line);
			if (m.matches()) {
				return parseBitAssignments(m.group(1), m.group(2));
			}
		}
		return Command.of(Command.Type.UNRECOGNIZED);
	}

	private Command parseSimulateTrain(String lengthToken, String capacitySpec, String delaySpec, String pauseToken) {
		int length = (int) parseLong(lengthToken, "train length");
		long pauseMs = (pauseToken != null) ? parseLong(pauseToken, "pause") : DEFAULT_SIMULATE_TRAIN_PAUSE_MS;

		Spec capacity = parseSpec(capacitySpec, "capacity");
		Spec delay = parseSpec(delaySpec, "delay");

		Integer uniformCapacity = (capacity.uniform != null) ? capacity.uniform.intValue() : null;
		Map<String, Integer> perTrackCapacity = null;
		if (capacity.perTrack != null) {
			perTrackCapacity = new LinkedHashMap<>();
			for (Map.Entry<String, Long> e : capacity.perTrack.entrySet()) {
				perTrackCapacity.put(e.getKey(), e.getValue().intValue());
			}
		}

		return Command.simulateTrain(length, uniformCapacity, perTrackCapacity, delay.uniform, delay.perTrack,
				pauseMs);
	}

	/** Holds the result of parsing a "<value>" or "<id>=<value>,<id>=<value>,..." token - exactly one field is set. */
	private static class Spec {
		Long uniform;
		Map<String, Long> perTrack;
	}

	/**
	 * A spec token is either a plain number (one value for every track) or a
	 * comma-separated list of <id>=<value> pairs, no spaces (each track gets its
	 * own value). Which identifiers actually exist isn't known here (that's
	 * Simulator's job, once it has the real track list) - this only parses the
	 * raw text.
	 */
	private Spec parseSpec(String token, String label) {
		Spec spec = new Spec();
		if (token.contains("=")) {
			spec.perTrack = new LinkedHashMap<>();
			for (String pair : token.split(",")) {
				String[] kv = pair.split("=");
				if (kv.length != 2 || kv[0].isEmpty()) {
					throw new IllegalArgumentException(
							"Can't understand " + label + " \"" + pair + "\" - expected <id>=<value>, e.g. \"1T=15\".");
				}
				spec.perTrack.put(kv[0], parseLong(kv[1], label + " for \"" + kv[0] + "\""));
			}
		} else {
			spec.uniform = parseLong(token, label);
		}
		return spec;
	}

	private Command parseBitAssignments(String assignmentsText, String identifier) {
		String[] segments = assignmentsText.split("(?i)\\s+and\\s+");
		Map<String, Integer> updates = new LinkedHashMap<>();
		for (String segment : segments) {
			Matcher am = BIT_ASSIGNMENT_PATTERN.matcher(segment.trim());
			if (!am.matches()) {
				throw new IllegalArgumentException(
						"Can't understand \"" + segment.trim() + "\" - expected e.g. \"ERR bit as 1\" or \"CE as 0\".");
			}
			updates.put(am.group(1).toUpperCase(), Integer.parseInt(am.group(2)));
		}
		return Command.setBits(identifier, updates);
	}

	private int parseInt(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			throw new NumberFormatException("Expected a number, got \"" + s + "\".");
		}
	}

	private long parseLong(String s, String label) {
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			throw new NumberFormatException("Expected a number for " + label + ", got \"" + s + "\".");
		}
	}
}