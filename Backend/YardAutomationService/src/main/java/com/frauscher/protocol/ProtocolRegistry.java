package com.frauscher.protocol;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.frauscher.yard.AppProperties;
import com.frauscher.yard.YardConfig;
import com.frauscher.yard.YardDiscovery;

/**
 * Holds the currently-active protocol definition (core rows + field tree +
 * yard/track configuration), loaded from an .xlsx file at startup and
 * reloadable at runtime via ProtocolReloadController - no restart needed.
 *
 * coreRows, packetTree, and yards always come from the SAME load, bundled
 * together in one Snapshot and swapped in as a single atomic reference update,
 * so a reader mid-packet never sees an inconsistent mix of old and new. A
 * failed reload (bad path, malformed sheet) throws and leaves the previous,
 * already-working snapshot untouched - live traffic is never disrupted by a bad
 * reload attempt.
 */
@Component
public class ProtocolRegistry {

	public record Snapshot(List<Row> coreRows, List<FieldNode> packetTree, List<YardConfig> yards, String sourcePath) {
	}

	private final AtomicReference<Snapshot> current = new AtomicReference<>();

	public ProtocolRegistry(AppProperties appProperties) throws IOException {
		reload(appProperties.protocolDefinitionPath());
	}

	/**
	 * Loads the given xlsx and swaps it in atomically. On failure, throws and the
	 * previous snapshot stays active.
	 */
	public synchronized void reload(String xlsxPath) throws IOException {
		List<Row> coreRows = ConfigLoader.loadCoreDefinition(xlsxPath);
		List<Row> payloadRows = ConfigLoader.loadPayloadDefinition(xlsxPath);
		List<FieldNode> packetTree = PacketDecoder.buildFullTree(coreRows, payloadRows);
		List<YardConfig> yards = YardDiscovery.discover(payloadRows);
		current.set(new Snapshot(coreRows, packetTree, yards, xlsxPath));
	}

	public Snapshot current() {
		return current.get();
	}
}