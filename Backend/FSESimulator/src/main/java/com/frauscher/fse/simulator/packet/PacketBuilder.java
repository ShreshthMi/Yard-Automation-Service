package com.frauscher.fse.simulator.packet;

import java.util.Map;

import com.frauscher.fse.simulator.crc.Crc32;
import com.frauscher.fse.simulator.model.AxleSlot;

/**
 * Builds the current "wire payload" - Protocol Version through CRC32 Inverse -
 * from the simulator's state and the slot layout discovered from the
 * payload-definition sheet.
 */
public class PacketBuilder {

	// Fixed header field values for the simulated sender. Edit these if
	// your setup needs different addresses/ports.
	private static final int PROTOCOL_VERSION = 2;
	private static final long DESTINATION_ADDRESS = 3401;
	private static final long SOURCE_ADDRESS = 3466;
	private static final int DESTINATION_PORT = 10;
	private static final int SOURCE_PORT = 66;

	private final Map<String, AxleSlot> axleSlots;
	private final int headerLength; // bytes from Protocol Version up to (not including) Payload
	private final int payloadLength; // bytes of Payload, as declared by the config

	public PacketBuilder(Map<String, AxleSlot> axleSlots, int headerLength, int payloadLength) {
		this.axleSlots = axleSlots;
		this.headerLength = headerLength;
		this.payloadLength = payloadLength;
	}

	public byte[] build(SimulatorState state) {
		int payloadStart = headerLength;
		int crcStart = payloadStart + payloadLength;
		byte[] p = new byte[crcStart + 8]; // + CRC32 (4) + CRC32 Inverse (4)

		p[0] = (byte) PROTOCOL_VERSION;
		putUnsignedBE(p, 1, 4, DESTINATION_ADDRESS);
		putUnsignedBE(p, 5, 4, SOURCE_ADDRESS);
		p[9] = (byte) DESTINATION_PORT;
		p[10] = (byte) SOURCE_PORT;
		p[11] = (byte) 2; // RX Timestamp Control: no TX timestamp received yet
		putUnsignedBE(p, 12, 4, 0); // RX Timestamp: not valid
		p[16] = (byte) 0; // TX Timestamp Control: valid
		putUnsignedBE(p, 17, 4, state.nextTxTimestamp());

		// Payload bytes default to 0 (covers Check Byte and anything else not written
		// below).
		for (Map.Entry<String, AxleSlot> entry : axleSlots.entrySet()) {
			String id = entry.getKey();
			writeSlot(p, payloadStart + entry.getValue().offset, entry.getValue(), state.getAxleCount(id),
					state.getBitOverrides(id));
		}

		long crc32 = Crc32.compute(p, 0, crcStart);
		long crc32Inverse = Crc32.computeInverse(p, 0, crcStart);
		putUnsignedBE(p, crcStart, 4, crc32);
		putUnsignedBE(p, crcStart + 4, 4, crc32Inverse);

		return p;
	}

	/**
	 * Writes whatever fields this slot's definition actually has. For the Status
	 * word, every bit the sheet defines for this identifier gets a value, decided
	 * in this order: 1. A manual override (set via a bit command) - always wins. 2.
	 * Otherwise, CLR/OCC auto-derive from the axle count as usual. 3. Otherwise,
	 * defaults to 0. CATS/TL are written only if this slot's definition actually
	 * has them.
	 */
	private void writeSlot(byte[] p, int slotStart, AxleSlot slot, int axleCount, Map<String, Integer> bitOverrides) {
		if (slot.statusOffset != null) {
			long statusWord = 0;
			for (Map.Entry<String, Integer> bitEntry : slot.statusBits.entrySet()) {
				String bitName = bitEntry.getKey();
				int bitPos = bitEntry.getValue();
				int value;
				if (bitOverrides.containsKey(bitName)) {
					value = bitOverrides.get(bitName);
				} else if (bitName.equals("CLR")) {
					value = (axleCount == 0) ? 1 : 0;
				} else if (bitName.equals("OCC")) {
					value = (axleCount > 0) ? 1 : 0;
				} else {
					value = 0;
				}
				if (value != 0)
					statusWord |= (1L << bitPos);
			}
			putUnsignedBE(p, slotStart + slot.statusOffset, slot.statusLength, statusWord);
		}

		if (slot.catsOffset != null) {
			long catsMax = (1L << (8 * slot.catsLength)) - 1;
			putUnsignedBE(p, slotStart + slot.catsOffset, slot.catsLength, Math.min(axleCount, catsMax));
		}
		if (slot.tlOffset != null) {
			long tlMax = (1L << (8 * slot.tlLength)) - 1;
			putUnsignedBE(p, slotStart + slot.tlOffset, slot.tlLength, Math.min((long) axleCount * 10, tlMax));
		}
	}

	private static void putUnsignedBE(byte[] p, int offset, int length, long value) {
		for (int i = 0; i < length; i++) {
			int shift = 8 * (length - 1 - i);
			p[offset + i] = (byte) ((value >> shift) & 0xFF);
		}
	}
}
