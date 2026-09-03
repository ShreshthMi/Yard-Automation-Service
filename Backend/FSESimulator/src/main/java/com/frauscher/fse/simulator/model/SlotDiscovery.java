package com.frauscher.fse.simulator.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.frauscher.fse.simulator.config.FieldNode;
import com.frauscher.fse.simulator.config.Row;

/**
 * Walks the payload field tree looking for identified groups ("track" slots
 * like "1T", "2T", ...) and reads whatever Status/CATS/TL fields each one
 * actually has. Nothing about a slot's shape is required - whatever the sheet
 * defines is used, whatever it doesn't is simply left out (see
 * {@link AxleSlot}).
 */
public class SlotDiscovery {

	/**
	 * Payload's own byte length: the furthest (offset+length) reached by its direct
	 * children.
	 */
	public static int computePayloadLength(List<FieldNode> payloadTopLevel) {
		int max = 0;
		for (FieldNode node : payloadTopLevel) {
			int end = Integer.parseInt(node.row.offset) + Integer.parseInt(node.row.length);
			max = Math.max(max, end);
		}
		return max;
	}

	/**
	 * Finds every identified group in the tree, at any depth, and builds an
	 * AxleSlot for each.
	 */
	public static Map<String, AxleSlot> discoverAxleSlots(List<FieldNode> payloadTopLevel) {
		Map<String, AxleSlot> slots = new LinkedHashMap<>();
		collect(payloadTopLevel, 0, slots);
		return slots;
	}

	private static void collect(List<FieldNode> nodes, int parentStart, Map<String, AxleSlot> slots) {
		for (FieldNode node : nodes) {
			Row r = node.row;
			if (!"group".equals(r.type))
				continue;
			int offset = parentStart + Integer.parseInt(r.offset);

			if (r.identifier != null) {
				AxleSlot slot = buildSlot(offset, node.children);
				slots.put(r.identifier, slot);
				warnIfIncomplete(r, slot);
			} else {
				collect(node.children, offset, slots); // keep descending, e.g. into a wrapper group
			}
		}
	}

	private static AxleSlot buildSlot(int offset, List<FieldNode> children) {
		Integer statusOffset = null, statusLength = null;
		Map<String, Integer> statusBits = new LinkedHashMap<>();
		Integer catsOffset = null, catsLength = null, tlOffset = null, tlLength = null;

		for (FieldNode child : children) {
			Row r = child.row;
			if ("group".equals(r.type) && "Status".equalsIgnoreCase(r.field)) {
				statusOffset = Integer.parseInt(r.offset);
				statusLength = Integer.parseInt(r.length);
				for (FieldNode bitNode : child.children) {
					Row br = bitNode.row;
					if ("bit".equals(br.type) && br.bit != null) {
						statusBits.put(br.field.toUpperCase(), br.bit);
					}
				}
			} else if ("CATS".equalsIgnoreCase(r.field)) {
				catsOffset = Integer.parseInt(r.offset);
				catsLength = Integer.parseInt(r.length);
			} else if ("TL".equalsIgnoreCase(r.field)) {
				tlOffset = Integer.parseInt(r.offset);
				tlLength = Integer.parseInt(r.length);
			}
		}
		return new AxleSlot(offset, statusOffset, statusLength, statusBits, catsOffset, catsLength, tlOffset, tlLength);
	}

	private static void warnIfIncomplete(Row r, AxleSlot slot) {
		if (slot.statusOffset == null) {
			System.err.println("Note: identifier \"" + r.identifier + "\" (Excel row " + r.excelRow
					+ ") has no Status group in its definition - it'll accept commands but"
					+ " nothing will actually change in the packet for it.");
			return;
		}
		boolean hasClr = slot.statusBits.containsKey("CLR");
		boolean hasOcc = slot.statusBits.containsKey("OCC");
		if (!hasClr || !hasOcc) {
			System.err.println("Note: identifier \"" + r.identifier + "\" (Excel row " + r.excelRow + ") is missing "
					+ (!hasClr ? "CLR" : "") + (!hasClr && !hasOcc ? "/" : "") + (!hasOcc ? "OCC" : "")
					+ " in its Status group - that bit won't auto-set from axle count"
					+ " (it can still be set manually with a bit command if the sheet ever adds it).");
		}
	}
}
