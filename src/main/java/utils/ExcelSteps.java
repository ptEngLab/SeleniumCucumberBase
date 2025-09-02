package utils;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExcelSteps {

    private static final Logger logger = LogManager.getLogger(ExcelSteps.class);

    private final TestData testData;

    public ExcelSteps(TestData testData) {
        this.testData = testData;
    }

    /** Get the workbook for current scenario/thread */
    public XSSFWorkbook getWorkbook() {
        XSSFWorkbook workbook = testData.getWorkBook();
        if (workbook == null) {
            throw new RuntimeException("Workbook not initialized for scenario: " + testData.getScenarioName());
        }
        return workbook;
    }

    /** Get the sheet for current scenario/thread */
    public XSSFSheet getSheet() {
        XSSFSheet sheet = testData.getWorkSheet();
        if (sheet == null) {
            throw new RuntimeException("Sheet not initialized for scenario: " + testData.getScenarioName());
        }
        return sheet;
    }

    /** Read cell value by row and column index */
    public String readCell(int rowNum, int colNum) {
        return ExcelUtils.getCellData(getSheet(), rowNum, colNum);
    }

    /** Read cell value by row and column name (first row as header) */
    public String readCell(int rowNum, String colName) {
        int colNum = ExcelUtils.getColNo(getSheet(), colName);
        if (colNum == -1) {
            throw new RuntimeException("Column not found: " + colName);
        }
        return readCell(rowNum, colNum);
    }

    /** Write value to a cell */
    public void writeCell(int rowNum, int colNum, String value) {
        ExcelUtils.setCellData(getSheet(), rowNum, colNum, value);
    }

    /** Write value to a cell by column name */
    public void writeCell(int rowNum, String colName, String value) {
        int colNum = ExcelUtils.getColNo(getSheet(), colName);
        if (colNum == -1) {
            throw new RuntimeException("Column not found: " + colName);
        }
        writeCell(rowNum, colNum, value);
    }

    /** Save the workbook for the current scenario */
    public void save() {
        ExcelUtils.saveWorkbook(getWorkbook(), testData.getTestDataFile());
    }
}
