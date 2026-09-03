package com.frauscher.protocol;

/**
 * CRC32 / CRC32 Inverse calculation for the FSE protocol.
 *
 * Classic reflected CRC-32 algorithm (the same style used by Ethernet/zlib
 * crc32), with the CRC-32Q polynomial swapped in:
 *   poly (reflected) = 0xD5828281   (bit-reversal of CRC-32Q's 0x814141AB)
 *   init              = 0xFFFFFFFF
 *   xorout            = 0xFFFFFFFF
 *
 * CRC32 is computed over the given byte range as-is. CRC32 Inverse is the
 * same algorithm over the same range with every byte bitwise-inverted
 * first ("calculated over the inverted data").
 */
public class Crc32 {

    private static final long REVERSED_POLY = 0xD5828281L;
    private static final long[] TABLE = buildTable();

    private static long[] buildTable() {
        long[] table = new long[256];
        for (int i = 0; i < 256; i++) {
            long c = i;
            for (int bit = 0; bit < 8; bit++) {
                c = ((c & 1L) != 0L) ? (REVERSED_POLY ^ (c >>> 1)) : (c >>> 1);
            }
            table[i] = c;
        }
        return table;
    }

    /** CRC32 over data[rangeStart, rangeEnd). */
    public static long compute(byte[] data, int rangeStart, int rangeEnd) {
        return compute(data, rangeStart, rangeEnd, false);
    }

    /** CRC32 Inverse over data[rangeStart, rangeEnd) - same algorithm, bytes inverted first. */
    public static long computeInverse(byte[] data, int rangeStart, int rangeEnd) {
        return compute(data, rangeStart, rangeEnd, true);
    }

    private static long compute(byte[] data, int rangeStart, int rangeEnd, boolean invertBytesFirst) {
        long crc = 0xFFFFFFFFL;
        for (int i = rangeStart; i < rangeEnd; i++) {
            int b = data[i] & 0xFF;
            if (invertBytesFirst) b = (~b) & 0xFF;
            crc = TABLE[(int) ((crc ^ b) & 0xFF)] ^ (crc >>> 8);
        }
        return (crc ^ 0xFFFFFFFFL) & 0xFFFFFFFFL;
    }
}
