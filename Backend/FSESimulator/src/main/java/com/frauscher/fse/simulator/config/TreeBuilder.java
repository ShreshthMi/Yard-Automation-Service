package com.frauscher.fse.simulator.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the flat, ordered list of rows from a sheet into a tree.
 *
 * The sheet is written as a pre-order walk of the tree: every row's "Parent"
 * column names the Field of the row it belongs under, and a group's children
 * are exactly the rows that immediately follow it whose Parent matches the
 * group's own Field name. A single pass with a shared read position builds the
 * whole tree.
 */
public class TreeBuilder {

	/**
	 * @param rows       the full, ordered row list for one sheet
	 * @param parentName the Field name a row's Parent column must match to be
	 *                   consumed here (the sheet's root parent, e.g. "Payload")
	 * @param cursor     single-element array used as a shared read position into
	 *                   rows
	 */
	public static List<FieldNode> build(List<Row> rows, String parentName, int[] cursor) {
		List<FieldNode> result = new ArrayList<>();
		while (cursor[0] < rows.size() && rows.get(cursor[0]).parent.equalsIgnoreCase(parentName)) {
			Row r = rows.get(cursor[0]);
			cursor[0]++;
			FieldNode node = new FieldNode(r);
			if ("group".equals(r.type)) {
				node.children.addAll(build(rows, r.field, cursor));
			}
			result.add(node);
		}
		return result;
	}
}
