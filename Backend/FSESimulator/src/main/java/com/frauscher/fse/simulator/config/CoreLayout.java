package com.frauscher.fse.simulator.config;

import java.util.List;

/**
 * Looks up a top-level (Parent = "Packet") field's byte offset from the
 * core-definition rows.
 */
public class CoreLayout {

	public static int findOffset(List<Row> coreRows, String fieldName) {
		for (Row r : coreRows) {
			if ("Packet".equalsIgnoreCase(r.parent) && fieldName.equalsIgnoreCase(r.field)) {
				return Integer.parseInt(r.offset);
			}
		}
		throw new IllegalStateException("Field not found in core definition: " + fieldName);
	}
}
