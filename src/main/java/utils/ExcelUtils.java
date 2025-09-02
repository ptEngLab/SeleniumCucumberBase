package utils;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;

import static utils.Constants.DD_MM_YYYY;
import static utils.Constants.EMPTY_STRING;

public class ExcelUtils {

    private static final Logger logger = LogManager.getLogger(ExcelUtils.class);

    // Thread-local date formatter for parallel safety
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMATTER =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(DD_MM_YYYY));

    // -------------------------
    // Workbook / Sheet methods
    // -------------------------

    public static XSSFWorkbook openWorkbook(String filePath) {
        try (FileInputStream excelFile = new FileInputStream(filePath)) {
            return new XSSFWorkbook(excelFile);
        } catch (Exception e) {
            logger.error("Failed to open Excel workbook: {}", filePath, e);
            throw new RuntimeException("Could not open Excel workbook: " + filePath, e);
        }
    }

    public static XSSFSheet getSheet(XSSFWorkbook workbook, String sheetName) {
        XSSFSheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            logger.error("Sheet not found: {}", sheetName);
            throw new RuntimeException("Sheet not found: " + sheetName);
        }
        return sheet;
    }

    public static synchronized void saveWorkbook(XSSFWorkbook workbook, String filePath) {
        if (workbook == null) {
            throw new RuntimeException("No workbook provided for saving");
        }

        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        } catch (Exception e) {
            logger.error("Failed to save Excel workbook: {}", filePath, e);
            throw new RuntimeException("Could not save Excel workbook: " + filePath, e);
        }
    }

    // -------------------------
    // Cell data methods
    // -------------------------

    public static String getCellData(XSSFSheet sheet, int rowNum, int colNum) {
        try {
            var row = sheet.getRow(rowNum);
            if (row == null) return EMPTY_STRING;

            var cell = row.getCell(colNum);
            if (cell == null) return EMPTY_STRING;

            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield DATE_FORMATTER.get().format(cell.getDateCellValue());
                    } else {
                        double val = cell.getNumericCellValue();
                        yield val == (long) val ? String.valueOf((long) val) : String.valueOf(val);
                    }
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> cell.getCellFormula();
                default -> EMPTY_STRING;
            };
        } catch (Exception e) {
            logger.error("Failed to get cell data at row: {}, col: {}", rowNum, colNum, e);
            return EMPTY_STRING;
        }
    }

    public static void setCellData(XSSFSheet sheet, int rowNum, int colNum, String value) {
        try {
            var row = sheet.getRow(rowNum);
            if (row == null) row = sheet.createRow(rowNum);

            XSSFCell cell = row.getCell(colNum);
            if (cell == null) cell = row.createCell(colNum);

            cell.setCellValue(value);
        } catch (Exception e) {
            logger.error("Failed to set cell data at row: {}, col: {}", rowNum, colNum, e);
            throw new RuntimeException("Could not set cell data at row: " + rowNum + ", col: " + colNum, e);
        }
    }

    // -------------------------
    // Utility methods
    // -------------------------

    public static int getColNo(XSSFSheet sheet, String colName) {
        var headerRow = sheet.getRow(0);
        for (int colNum = 0; colNum < headerRow.getLastCellNum(); colNum++) {
            var cell = headerRow.getCell(colNum);
            if (cell != null && cell.getCellType() == CellType.STRING &&
                    colName.equalsIgnoreCase(cell.getStringCellValue())) {
                return colNum;
            }
        }
        logger.error("Column not found: {}", colName);
        return -1;
    }

    public static int getRow(XSSFSheet sheet, String scenarioName) {
        int rowCount = sheet.getLastRowNum();
        for (int rowNum = 1; rowNum <= rowCount; rowNum++) {
            var row = sheet.getRow(rowNum);
            if (row != null) {
                var cell = row.getCell(0);
                if (cell != null && scenarioName.equalsIgnoreCase(cell.getStringCellValue())) {
                    return rowNum;
                }
            }
        }
        logger.error("Test case not found: {}", scenarioName);
        return -1;
    }

    public static int getTotalColumns(XSSFSheet sheet) {
        var headerRow = sheet.getRow(0);
        if (headerRow == null) {
            logger.error("Header row is missing in the sheet");
            throw new RuntimeException("Header row is missing in the sheet");
        }
        return headerRow.getLastCellNum();
    }

}
