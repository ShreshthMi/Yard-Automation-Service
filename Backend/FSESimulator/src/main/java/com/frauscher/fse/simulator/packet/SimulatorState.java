package com.frauscher.fse.simulator.packet;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds the simulator's mutable state: current axle count and any manually set
 * status bits, tracked independently for every identifier ("1T", "2T", ...).
 * State persists across commands until explicitly changed again.
 */
public class SimulatorState {

	private final Map<String, Integer> axleCounts = new LinkedHashMap<>();
	private final Map<String, Map<String, Integer>> manualBits = new LinkedHashMap<>();
	private long txTimestamp = 0;

	public SimulatorState(Set<String> identifiers) {
		for (String id : identifiers) {
			axleCounts.put(id, 0);
			manualBits.put(id, new LinkedHashMap<>());
		}
	}

	public int getAxleCount(String id) {
		return axleCounts.get(id);
	}

	public int addAxles(String id, int n) {
		int newCount = Math.max(0, axleCounts.get(id) + n);
		axleCounts.put(id, newCount);
		return newCount;
	}

	public int removeAxles(String id, int n) {
		int newCount = Math.max(0, axleCounts.get(id) - n);
		axleCounts.put(id, newCount);
		return newCount;
	}

	public void removeAllAxles(String id) {
		axleCounts.put(id, 0);
	}

	/**
	 * Sets an identifier's axle count directly, rather than adding/removing a
	 * delta - used when a value is already computed elsewhere (e.g. "simulate
	 * train"'s per-zone overlap with the train's body), not accumulated one axle
	 * at a time. Clamped to zero or above, same as addAxles/removeAxles.
	 */
	public void setAxleCount(String id, int count) {
		axleCounts.put(id, Math.max(0, count));
	}

	/**
	 * Records manual bit overrides for an identifier; they persist until changed
	 * again.
	 */
	public void setBits(String id, Map<String, Integer> updates) {
		manualBits.get(id).putAll(updates);
	}

	public Map<String, Integer> getBitOverrides(String id) {
		return manualBits.get(id);
	}

	/**
	 * Returns the current TX timestamp tick, then advances it by one (each tick =
	 * 10ms).
	 */
	public long nextTxTimestamp() {
		long current = txTimestamp;
		txTimestamp += 1;
		return current;
	}
}