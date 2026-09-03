package com.frauscher.fse.simulator.model;

import java.util.Map;

import com.frauscher.fse.simulator.packet.PacketBuilder;

/**
 * The layout of one identified "track" slot (e.g. "1T"), as discovered
 * from the payload-definition sheet by {@link SlotDiscovery}.
 *
 * Every field is optional except offset (null = "this identifier's
 * definition doesn't define that field") - whatever is present gets
 * written by {@link PacketBuilder}; whatever is missing is simply
 * skipped, never an error.
 */
public class AxleSlot {

    public final int offset;                 // absolute offset within the payload (bytes)
    public final Integer statusOffset;       // relative to slot start, or null if no Status group
    public final Integer statusLength;       // bytes, or null
    public final Map<String, Integer> statusBits; // bit name (upper-case) -> bit position; whatever the sheet defines (CLR, OCC, ERR, CE, ...)
    public final Integer catsOffset;         // relative to slot start, or null if no CATS field
    public final Integer catsLength;         // bytes, or null
    public final Integer tlOffset;           // relative to slot start, or null if no TL field
    public final Integer tlLength;           // bytes, or null

    public AxleSlot(int offset, Integer statusOffset, Integer statusLength, Map<String, Integer> statusBits,
                     Integer catsOffset, Integer catsLength, Integer tlOffset, Integer tlLength) {
        this.offset = offset;
        this.statusOffset = statusOffset;
        this.statusLength = statusLength;
        this.statusBits = statusBits;
        this.catsOffset = catsOffset;
        this.catsLength = catsLength;
        this.tlOffset = tlOffset;
        this.tlLength = tlLength;
    }
}
