package com.frauscher.ingest;

import org.springframework.stereotype.Component;

import com.frauscher.protocol.CoreLayout;
import com.frauscher.protocol.Crc32;
import com.frauscher.protocol.ProtocolRegistry;
import com.frauscher.protocol.Row;

import java.util.List;

/** Checks CRC32 and CRC32 Inverse on a raw (already header-padded) packet before any decoding happens. */
@Component
public class PacketValidator {

    private final ProtocolRegistry protocolRegistry;

    public PacketValidator(ProtocolRegistry protocolRegistry) {
        this.protocolRegistry = protocolRegistry;
    }

    public ValidationResult validate(byte[] packet) {
        List<Row> coreRows = protocolRegistry.current().coreRows(); // always the latest reloaded definition
        int total = packet.length;
        int protocolVersionOffset = CoreLayout.findOffset(coreRows, "Protocol Version");
        int crc32Offset = CoreLayout.findOffset(coreRows, "CRC32", total);
        int crc32InverseOffset = CoreLayout.findOffset(coreRows, "CRC32 Inverse", total);

        if (crc32InverseOffset + 4 > total || crc32Offset < protocolVersionOffset) {
            return ValidationResult.invalid("Packet too short (" + total + " bytes) to contain a valid header and CRC fields");
        }

        long receivedCrc32 = readUnsignedBigEndian(packet, crc32Offset, 4);
        long receivedCrc32Inverse = readUnsignedBigEndian(packet, crc32InverseOffset, 4);

        long computedCrc32 = Crc32.compute(packet, protocolVersionOffset, crc32Offset);
        long computedCrc32Inverse = Crc32.computeInverse(packet, protocolVersionOffset, crc32Offset);

        if (computedCrc32 != receivedCrc32) {
            return ValidationResult.invalid(String.format(
                    "CRC32 mismatch (received=%08X, computed=%08X)", receivedCrc32, computedCrc32));
        }
        if (computedCrc32Inverse != receivedCrc32Inverse) {
            return ValidationResult.invalid(String.format(
                    "CRC32 Inverse mismatch (received=%08X, computed=%08X)", receivedCrc32Inverse, computedCrc32Inverse));
        }
        return ValidationResult.ok();
    }

    private static long readUnsignedBigEndian(byte[] data, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }
}