package com.frauscher.protocol;

/**
 * One row from either the "core definition" or "payload definition" sheet of
 * protocol_definition.xlsx. offset/length are kept as Strings because they can
 * be plain numbers ("34") or expressions ("Last-8", "Remaining-8").
 *
 * yardName/occupiedMessage1/occupiedMessage2 only apply to identifier rows in
 * the payload-definition sheet (e.g. the "1T" row under an AEB_FMA group) -
 * they are how a track section is assigned to a yard and given its occupied
 * message/warning directly in the spreadsheet, instead of a separate properties
 * file. A row with no Yard Name is not part of any yard - it still decodes
 * normally, it's just not surfaced to the yard-automation/notification layer.
 */
public class Row {

	public final String parent;
	public final String field;
	public final String identifier; // may be null
	public final String type; // bytes | int | long | group | bit
	public final String offset; // may be null for bit rows
	public final String length; // may be null
	public final Integer bit; // only for type = "bit"
	public final String yardName; // may be null - only set on identifier rows that belong to a yard
	public final String occupiedMessage1; // may be null - shown when this identifier's CLR = 0
	public final String occupiedMessage2; // may be null - shown when this identifier's CLR = 0
	public final int excelRow; // 1-based Excel row number, for diagnostics (-1 if unknown)

	public Row(String parent, String field, String identifier, String type, String offset, String length, Integer bit,
			String yardName, String occupiedMessage1, String occupiedMessage2, int excelRow) {
		this.parent = parent == null ? "" : parent.trim();
		this.field = field == null ? "" : field.trim();
		this.identifier = (identifier == null || identifier.trim().isEmpty()) ? null : identifier.trim();
		this.type = type == null ? "" : type.trim().toLowerCase();
		this.offset = (offset == null || offset.trim().isEmpty()) ? null : offset.trim();
		this.length = (length == null || length.trim().isEmpty()) ? null : length.trim();
		this.bit = bit;
		this.yardName = (yardName == null || yardName.trim().isEmpty()) ? null : yardName.trim();
		this.occupiedMessage1 = (occupiedMessage1 == null || occupiedMessage1.trim().isEmpty()) ? null
				: occupiedMessage1.trim();
		this.occupiedMessage2 = (occupiedMessage2 == null || occupiedMessage2.trim().isEmpty()) ? null
				: occupiedMessage2.trim();
		this.excelRow = excelRow;
	}

	@Override
	public String toString() {
		return "Row{excelRow=" + excelRow + ", parent=" + parent + ", field=" + field + ", id=" + identifier + ", type="
				+ type + ", offset=" + offset + ", length=" + length + ", bit=" + bit + ", yardName=" + yardName + "}";
	}
}