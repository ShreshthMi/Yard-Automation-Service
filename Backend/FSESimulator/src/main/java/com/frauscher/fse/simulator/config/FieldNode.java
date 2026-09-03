package com.frauscher.fse.simulator.config;

import java.util.ArrayList;
import java.util.List;

/** A row plus its child rows (children only exist for type = "group"). */
public class FieldNode {
	public final Row row;
	public final List<FieldNode> children = new ArrayList<>();

	public FieldNode(Row row) {
		this.row = row;
	}
}
