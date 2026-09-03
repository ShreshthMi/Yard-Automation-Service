package com.frauscher.fse.simulator.command;

import java.util.Map;

/**
 * A parsed command line. Fields not relevant to {@link #type} are simply null.
 */
public class Command {

	public enum Type {
		EMPTY, EXIT, HELP, STATUS, SEND_AXLE, REMOVE_AXLE, REMOVE_ALL_AXLE, SET_BITS, CYCLE, SIMULATE_TRAIN, UNRECOGNIZED
	}

	public final Type type;
	public final Integer n; // axle count, for SEND_AXLE / REMOVE_AXLE / CYCLE
	public final String identifier; // raw, as typed - not yet validated against known identifiers
	public final Map<String, Integer> bitUpdates; // for SET_BITS: bit name (upper-case) -> 0/1
	public final Long delayMs; // for CYCLE, and SIMULATE_TRAIN when using one delay for every track
	public final Long pauseMs; // for SIMULATE_TRAIN: pause once the train reaches the wall, before it leaves
	public final Map<String, Long> perTrackDelayMs; // for SIMULATE_TRAIN when each track has its own delay

	public final Integer trainLength; // for SIMULATE_TRAIN: the train's fixed length, in axles
	public final Integer uniformCapacity; // for SIMULATE_TRAIN when every track holds the same number of axles
	public final Map<String, Integer> perTrackCapacity; // for SIMULATE_TRAIN when each track has its own capacity

	private Command(Type type, Integer n, String identifier, Map<String, Integer> bitUpdates, Long delayMs,
			Long pauseMs, Map<String, Long> perTrackDelayMs, Integer trainLength, Integer uniformCapacity,
			Map<String, Integer> perTrackCapacity) {
		this.type = type;
		this.n = n;
		this.identifier = identifier;
		this.bitUpdates = bitUpdates;
		this.delayMs = delayMs;
		this.pauseMs = pauseMs;
		this.perTrackDelayMs = perTrackDelayMs;
		this.trainLength = trainLength;
		this.uniformCapacity = uniformCapacity;
		this.perTrackCapacity = perTrackCapacity;
	}

	public static Command of(Type type) {
		return new Command(type, null, null, null, null, null, null, null, null, null);
	}

	public static Command axle(Type type, int n, String identifier) {
		return new Command(type, n, identifier, null, null, null, null, null, null, null);
	}

	public static Command removeAll(String identifier) {
		return new Command(Type.REMOVE_ALL_AXLE, null, identifier, null, null, null, null, null, null, null);
	}

	public static Command setBits(String identifier, Map<String, Integer> bitUpdates) {
		return new Command(Type.SET_BITS, null, identifier, bitUpdates, null, null, null, null, null, null);
	}

	public static Command cycle(int n, String identifier, long delayMs) {
		return new Command(Type.CYCLE, n, identifier, null, delayMs, null, null, null, null, null);
	}

	/**
	 * simulate train length <n> capacity <spec> delay <spec> [pause <ms>] - both
	 * capacity and delay are independently either one value for every track, or a
	 * per-track <id>=<value> list. Exactly one of {uniformCapacity,
	 * perTrackCapacity} is set, and exactly one of {delayMs, perTrackDelayMs} is
	 * set - Simulator resolves whichever pair was actually given.
	 */
	public static Command simulateTrain(int trainLength, Integer uniformCapacity,
			Map<String, Integer> perTrackCapacity, Long delayMs, Map<String, Long> perTrackDelayMs, long pauseMs) {
		return new Command(Type.SIMULATE_TRAIN, null, null, null, delayMs, pauseMs, perTrackDelayMs, trainLength,
				uniformCapacity, perTrackCapacity);
	}
}