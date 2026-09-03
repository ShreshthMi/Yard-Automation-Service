package com.frauscher.fse.simulator.config;

/**
 * One row from either the "core definition" or "payload definition" sheet
 * of protocol_definition.xlsx. offset/length are kept as Strings because
 * they can be plain numbers ("34") or expressions ("Last-8").
 */
public class Row {

    public final String parent;
    public final String field;
    public final String identifier; // may be null
    public final String type;       // bytes | int | long | group | bit
    public final String offset;     // may be null for bit rows
    public final String length;     // may be null
    public final Integer bit;       // only for type = "bit"
    public final int excelRow;      // 1-based Excel row number, for diagnostics (-1 if unknown)

	public Row(String parent, String field, String identifier, String type, String offset, String length, Integer bit,
			int excelRow) {
		this.parent = parent == null ? "" : parent.trim();
		this.field = field == null ? "" : field.trim();
		this.identifier = (identifier == null || identifier.trim().isEmpty()) ? null : identifier.trim();
		this.type = type == null ? "" : type.trim().toLowerCase();
		this.offset = (offset == null || offset.trim().isEmpty()) ? null : offset.trim();
		this.length = (length == null || length.trim().isEmpty()) ? null : length.trim();
		this.bit = bit;
		this.excelRow = excelRow;
	}

	@Override
	public String toString() {
		return "Row{excelRow=" + excelRow + ", parent=" + parent + ", field=" + field + ", id=" + identifier + ", type="
				+ type + ", offset=" + offset + ", length=" + length + ", bit=" + bit + "}";
	}
}
