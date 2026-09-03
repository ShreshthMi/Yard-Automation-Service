package com.frauscher.protocol;

import java.util.List;

/** Looks up a top-level (Parent = "Packet") field's byte offset from the core-definition rows. */
public class CoreLayout {

    /** For fields with a plain numeric offset (e.g. "Protocol Version", "Payload"). */
    public static int findOffset(List<Row> coreRows, String fieldName) {
        return findOffset(coreRows, fieldName, 0);
    }

    /** For fields whose offset may also be a "Last-N" expression (e.g. "CRC32"), given the packet's total length. */
    public static int findOffset(List<Row> coreRows, String fieldName, int totalLength) {
        for (Row r : coreRows) {
            if ("Packet".equalsIgnoreCase(r.parent) && fieldName.equalsIgnoreCase(r.field)) {
                if (r.offset.startsWith("Last-")) {
                    return totalLength - Integer.parseInt(r.offset.substring("Last-".length()));
                }
                return Integer.parseInt(r.offset);
            }
        }
        throw new IllegalStateException("Field not found in core definition: " + fieldName);
    }
}
