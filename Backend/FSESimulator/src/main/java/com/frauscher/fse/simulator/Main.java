package com.frauscher.fse.simulator;

import java.util.List;
import java.util.Map;

import com.frauscher.fse.simulator.app.Simulator;
import com.frauscher.fse.simulator.config.ConfigLoader;
import com.frauscher.fse.simulator.config.CoreLayout;
import com.frauscher.fse.simulator.config.FieldNode;
import com.frauscher.fse.simulator.config.Row;
import com.frauscher.fse.simulator.config.TreeBuilder;
import com.frauscher.fse.simulator.model.AxleSlot;
import com.frauscher.fse.simulator.model.SlotDiscovery;
import com.frauscher.fse.simulator.net.UdpSender;
import com.frauscher.fse.simulator.packet.PacketBuilder;
import com.frauscher.fse.simulator.packet.SimulatorState;

/**
 * FSEsimulator entry point.
 *
 * Usage: java -jar FSEsimulator.jar <protocol_definition.xlsx> <host> <port>
 *
 * Sends only the real "wire payload" - Protocol Version through CRC32 Inverse -
 * exactly what an actual UDP application payload would contain. There's no
 * Ethernet/IP/UDP header data to send here: those are added by the OS/network
 * stack for a real socket, not something an application puts inside its own UDP
 * payload.
 */
public class Main {

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.println("Usage: java -jar FSEsimulator.jar <protocol_definition.xlsx> <host> <port>");
			System.exit(1);
		}
		String xlsxPath = args[0];
		String host = args[1];
		int port = Integer.parseInt(args[2]);

		List<Row> coreRows = ConfigLoader.loadCoreDefinition(xlsxPath);
		List<Row> payloadRows = ConfigLoader.loadPayloadDefinition(xlsxPath);
		List<FieldNode> payloadTree = TreeBuilder.build(payloadRows, "Payload", new int[] { 0 });

		int headerLength = CoreLayout.findOffset(coreRows, "Payload")
				- CoreLayout.findOffset(coreRows, "Protocol Version");
		int payloadLength = SlotDiscovery.computePayloadLength(payloadTree);
		Map<String, AxleSlot> axleSlots = SlotDiscovery.discoverAxleSlots(payloadTree);

		if (axleSlots.isEmpty()) {
			throw new IllegalStateException(
					"No track slots (identified groups) found in the payload-definition sheet.");
		}

		SimulatorState state = new SimulatorState(axleSlots.keySet());
		PacketBuilder packetBuilder = new PacketBuilder(axleSlots, headerLength, payloadLength);
		UdpSender sender = new UdpSender(host, port);

		new Simulator(axleSlots, state, packetBuilder, sender).run();
	}
}
