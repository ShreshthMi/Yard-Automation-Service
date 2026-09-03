package com.frauscher.fse.simulator.config;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the two sheets of protocol_definition.xlsx into plain Row lists.
 *
 * Expected columns: - "core definition" : Parent | Field | Type | Offset |
 * Length - "payload definition" : Parent | Field | Identifier | Type | Offset |
 * Length | Bit
 *
 * The first row of each sheet is a header and is skipped. Rows whose
 * Parent/Field are both blank are ignored silently (expected - notes, spacer
 * rows). Any OTHER row that can't be read (Parent/Field present but Type
 * unreadable) gets a loud warning with its exact Excel row number instead of
 * being silently dropped, since the tree is built by reading rows strictly in
 * order - one dropped row shifts everything that follows it.
 */
public class ConfigLoader {

	public static List<Row> loadCoreDefinition(String xlsxPath) throws IOException {
		Sheet sheet = openSheet(xlsxPath, "core definition");
		List<Row> rows = new ArrayList<>();
		for (org.apache.poi.ss.usermodel.Row excelRow : sheet) {
			if (excelRow.getRowNum() == 0)
				continue; // header
			int excelRowNumber = excelRow.getRowNum() + 1;
			String parent = text(excelRow, 0);
			String field = text(excelRow, 1);
			if (isBlank(parent) && isBlank(field))
				continue;
			String type = text(excelRow, 2);
			if (isBlank(type)) {
				warnDroppedRow("core definition", excelRowNumber, parent, field);
				continue;
			}
			String offset = text(excelRow, 3);
			String length = text(excelRow, 4);
			rows.add(new Row(parent, field, null, type, offset, length, null, excelRowNumber));
		}
		return rows;
	}

	public static List<Row> loadPayloadDefinition(String xlsxPath) throws IOException {
		Sheet sheet = openSheet(xlsxPath, "payload definition");
		List<Row> rows = new ArrayList<>();
		for (org.apache.poi.ss.usermodel.Row excelRow : sheet) {
			if (excelRow.getRowNum() == 0)
				continue; // header
			int excelRowNumber = excelRow.getRowNum() + 1;
			String parent = text(excelRow, 0);
			String field = text(excelRow, 1);
			if (isBlank(parent) && isBlank(field))
				continue;
			String identifier = text(excelRow, 2);
			String type = text(excelRow, 3);
			if (isBlank(type)) {
				warnDroppedRow("payload definition", excelRowNumber, parent, field);
				continue;
			}
			String offset = text(excelRow, 4);
			String length = text(excelRow, 5);
			String bitText = text(excelRow, 6);
			Integer bit;
			try {
				bit = isBlank(bitText) ? null : (int) Double.parseDouble(bitText);
			} catch (NumberFormatException e) {
				System.err.printf(
						"Warning: payload definition, Excel row %d (Parent=\"%s\", Field=\"%s\"): "
								+ "Bit column is not a number (\"%s\") - treating as no bit.%n",
						excelRowNumber, parent, field, bitText);
				bit = null;
			}
			rows.add(new Row(parent, field, identifier, type, offset, length, bit, excelRowNumber));
		}
		return rows;
	}

	private static void warnDroppedRow(String sheetName, int excelRowNumber, String parent, String field) {
		System.err.printf("Warning: %s sheet, Excel row %d (Parent=\"%s\", Field=\"%s\") has no readable Type value - "
				+ "this row is being SKIPPED, which will shift how every later row is interpreted. "
				+ "Check that row in the spreadsheet.%n", sheetName, excelRowNumber, parent, field);
	}

	private static Sheet openSheet(String xlsxPath, String sheetNameWanted) throws IOException {
		try (FileInputStream fis = new FileInputStream(xlsxPath); Workbook workbook = new XSSFWorkbook(fis)) {
			FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
			for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
				Sheet s = workbook.getSheetAt(i);
				if (s.getSheetName().trim().equalsIgnoreCase(sheetNameWanted)) {
					// Copy the sheet's data out before the workbook (and its
					// backing stream) is closed by this try-with-resources.
					return cloneSheetInMemory(s, evaluator);
				}
			}
			throw new IOException("Sheet not found: " + sheetNameWanted);
		}
	}

	/**
	 * POI sheets become unusable once the source Workbook is closed, so pull every
	 * cell into a small in-memory copy first.
	 */
	private static Sheet cloneSheetInMemory(Sheet source, FormulaEvaluator evaluator) {
		Workbook temp = new XSSFWorkbook();
		Sheet copy = temp.createSheet();
		for (org.apache.poi.ss.usermodel.Row srcRow : source) {
			org.apache.poi.ss.usermodel.Row dstRow = copy.createRow(srcRow.getRowNum());
			for (Cell srcCell : srcRow) {
				Cell dstCell = dstRow.createCell(srcCell.getColumnIndex(), CellType.STRING);
				dstCell.setCellValue(cellToString(srcCell, evaluator));
			}
		}
		try {
			temp.close();
		} catch (IOException e) {
			System.out.println("Warning : Failed to close temp workbook");
		}
		return copy;
	}

	private static String text(org.apache.poi.ss.usermodel.Row excelRow, int col) {
		Cell cell = excelRow.getCell(col, MissingCellPolicy.RETURN_BLANK_AS_NULL);
		if (cell == null)
			return null;
		return cellToString(cell, null); // already resolved to a plain string by cloneSheetInMemory
	}

	private static String cellToString(Cell cell, FormulaEvaluator evaluator) {
		CellType type = cell.getCellType();
		if (type == CellType.FORMULA && evaluator != null) {
			type = evaluator.evaluateFormulaCell(cell);
		}
		switch (type) {
		case STRING:
			return cell.getStringCellValue().trim();
		case NUMERIC:
			double d = cell.getNumericCellValue();
			long asLong = (long) d;
			return (d == asLong) ? Long.toString(asLong) : Double.toString(d);
		case BLANK:
			return "";
		case BOOLEAN:
			return Boolean.toString(cell.getBooleanCellValue());
		case ERROR:
			return ""; // treat a formula error as blank rather than garbage text
		default:
			return cell.toString().trim();
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
