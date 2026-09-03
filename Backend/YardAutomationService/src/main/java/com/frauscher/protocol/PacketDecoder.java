package com.frauscher.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a field tree against raw packet bytes and produces a
 * LinkedHashMap / List / String / Long tree, ready for Jackson to
 * serialize directly. Ported unchanged (in behavior) from the original
 * CLI parser's PacketDecoder - see that project's comments for the full
 * rationale behind the offset/naming rules.
 *
 * Any field whose bytes don't actually fit in the packet decodes to
 * null and logs a warning, rather than throwing.
 */
public class PacketDecoder {

    private static final Logger log = LoggerFactory.getLogger(PacketDecoder.class);

    /** Builds the core tree with the payload-definition tree attached under its "Payload" node. */
    public static List<FieldNode> buildFullTree(List<Row> coreRows, List<Row> payloadRows) {
        List<FieldNode> coreTree = TreeBuilder.build(coreRows, "Packet", new int[]{0});
        List<FieldNode> payloadTree = TreeBuilder.build(payloadRows, "Payload", new int[]{0});
        attachPayloadTree(coreTree, payloadTree);
        return coreTree;
    }

    private static boolean attachPayloadTree(List<FieldNode> nodes, List<FieldNode> payloadTree) {
        for (FieldNode node : nodes) {
            if ("group".equals(node.row.type) && "payload".equalsIgnoreCase(node.row.field)) {
                node.children.clear();
                node.children.addAll(payloadTree);
                return true;
            }
            if (attachPayloadTree(node.children, payloadTree)) return true;
        }
        return false;
    }

    public static Map<String, Object> decode(List<FieldNode> topLevelNodes, byte[] data) {
        return decodeGroup(topLevelNodes, data, 0, data.length, data.length, false);
    }

    private static Map<String, Object> decodeGroup(List<FieldNode> nodes, byte[] data,
                                                     int parentStart, int parentLength,
                                                     int total, boolean payloadSheet) {
        Map<String, List<Object>> grouped = new LinkedHashMap<>();

        for (FieldNode node : nodes) {
            Row r = node.row;
            boolean childIsPayloadSheet = payloadSheet || isPayloadRoot(r);

            String key;
            Object value;

            if ("bit".equals(r.type)) {
                if (inBounds(data, parentStart, parentLength)) {
                    long container = unsignedBigEndian(data, parentStart, parentLength);
                    value = (int) ((container >> r.bit) & 1L);
                } else {
                    value = null;
                    log.warn("Field \"{}\" (bit {}) doesn't fit in this packet - decoded as null", r.field, r.bit);
                }
                key = r.identifier != null ? r.identifier : r.field;
            } else {
                int absOffset = resolveOffset(r.offset, parentStart, total);
                int length = resolveLength(r.length, absOffset, total);

                if ("group".equals(r.type)) {
                    if (absOffset < 0 || absOffset > data.length) {
                        value = new LinkedHashMap<String, Object>();
                        log.warn("Group \"{}\" (offset={}) doesn't fit in this packet ({} bytes) - decoded empty",
                                r.field, absOffset, data.length);
                    } else {
                        int clampedLength = Math.max(0, Math.min(length, data.length - absOffset));
                        value = decodeGroup(node.children, data, absOffset, clampedLength, total, childIsPayloadSheet);
                    }
                } else if (!inBounds(data, absOffset, length)) {
                    value = null;
                    log.warn("Field \"{}\" (offset={}, length={}) doesn't fit in this packet ({} bytes) - decoded as null",
                            r.field, absOffset, length, data.length);
                } else if ("bytes".equals(r.type)) {
                    value = toHex(data, absOffset, length);
                } else { // "int" or "long"
                    value = unsignedBigEndian(data, absOffset, length);
                }
                key = r.identifier != null ? r.identifier : jsonKey(r.field, payloadSheet && !"group".equals(r.type));
            }

            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Object>> e : grouped.entrySet()) {
            List<Object> values = e.getValue();
            result.put(e.getKey(), values.size() == 1 ? values.get(0) : values);
        }
        return result;
    }

    private static boolean isPayloadRoot(Row r) {
        return "group".equals(r.type) && "payload".equalsIgnoreCase(r.field);
    }

    private static boolean inBounds(byte[] data, int offset, int length) {
        return offset >= 0 && length >= 0 && offset + length <= data.length;
    }

    private static int resolveOffset(String offsetExpr, int parentStart, int total) {
        if (offsetExpr == null) return parentStart;
        if (offsetExpr.startsWith("Last-")) {
            int n = Integer.parseInt(offsetExpr.substring("Last-".length()));
            return total - n;
        }
        return parentStart + Integer.parseInt(offsetExpr);
    }

    private static int resolveLength(String lengthExpr, int absOffset, int total) {
        if (lengthExpr == null) return 0;
        if (lengthExpr.startsWith("Remaining-")) {
            int n = Integer.parseInt(lengthExpr.substring("Remaining-".length()));
            return total - absOffset - n;
        }
        return Integer.parseInt(lengthExpr);
    }

    private static long unsignedBigEndian(byte[] data, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }

    private static String toHex(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", data[offset + i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * camelCase for core-definition-sheet names and for all group names
     * (e.g. "Internet Protocol" -> "internetProtocol", "UDP" -> "udp").
     * Payload-sheet leaf fields (CATS, TL, CLR, ...) stay verbatim when
     * keepVerbatim is true, since those read as protocol-specific codes.
     */
    private static String jsonKey(String fieldName, boolean keepVerbatim) {
        if (keepVerbatim) return fieldName;
        String[] words = fieldName.trim().split("[ _]+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) continue;
            if (i == 0) {
                sb.append(w.toLowerCase());
            } else {
                sb.append(Character.toUpperCase(w.charAt(0)));
                sb.append(w.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
