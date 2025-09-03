package utils;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.lang.reflect.Method;

public class ExcelSteps {

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

   /** Read login credentials from a different sheet (e.g., "Credentials") without switching the main sheet*/


    public String readLoginCell(String sheetName, int rowNum, String colName) {
        XSSFWorkbook workbook = getWorkbook(); // current workbook
        XSSFSheet sheet = ExcelUtils.getSheet(workbook, sheetName); // load credential sheet
        int colNum = ExcelUtils.getColNo(sheet, colName);
        if (colNum == -1) {
            throw new RuntimeException("Column not found: " + colName + " in sheet: " + sheetName);
        }
        return ExcelUtils.getCellData(sheet, rowNum, colNum);
    }

    /** Get row number of a scenario/test case by scenario name (first column) */
    public int getRowNum(String scenarioName) {
        int rowNum = ExcelUtils.getRow(getSheet(), scenarioName);
        if (rowNum == -1) {
            throw new RuntimeException("Scenario not found in Excel: " + scenarioName);
        }
        return rowNum;
    }

    /**
     * Get row number in a specific sheet by first column value
     */
    public int getRowNum(String sheetName, String scenarioName) {
        XSSFWorkbook workbook = getWorkbook();
        XSSFSheet sheet = ExcelUtils.getSheet(workbook, sheetName);
        int rowNum = ExcelUtils.getRow(sheet, scenarioName);
        if (rowNum == -1) {
            throw new RuntimeException("Scenario not found in sheet " + sheetName + ": " + scenarioName);
        }
        return rowNum;
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


    public void loadScenarioData(String scenarioName) {
        XSSFSheet sheet = getSheet(); // GL Reports sheet
        int rowNum = ExcelUtils.getRow(sheet, scenarioName);
        if (rowNum == -1) {
            throw new RuntimeException("Scenario not found in GL Reports sheet: " + scenarioName);
        }

        // Get all methods of TestData
        Method[] methods = TestData.class.getMethods();

        for (int col = 0; col < sheet.getRow(0).getLastCellNum(); col++) {
            String columnName = sheet.getRow(0).getCell(col).getStringCellValue().trim();
            String cellValue = readCell(rowNum, columnName);

            // Build setter name dynamically: column_name -> setColumn_name
            String setterName = "set" + convertToCamelCase(columnName);

            // Find the setter method
            for (Method method : methods) {
                if (method.getName().equalsIgnoreCase(setterName) && method.getParameterCount() == 1) {
                    try {
                        method.invoke(testData, cellValue);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to set field '" + columnName + "' in TestData", e);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Converts "journal_batch_name" → "Journal_batch_name"
     */
    private String convertToCamelCase(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

/*
    public void loadScenarioData(String scenarioName) {
        XSSFSheet sheet = getSheet(); // GL Reports sheet is already loaded
        int rowNum = ExcelUtils.getRow(sheet, scenarioName);
        if (rowNum == -1) {
            throw new RuntimeException("Scenario not found in GL Reports sheet: " + scenarioName);
        }

        // Read values from Excel
        testData.setAppRole(readCell(rowNum, "role"));
        testData.setJournal_batch_name(readCell(rowNum, "journal_batch_name"));
        testData.setAccounting_period(readCell(rowNum, "accounting_period"));
        testData.setAccounting_source(readCell(rowNum, "accounting_source"));
        testData.setJournal_category(readCell(rowNum, "journal_category"));
        testData.setJournal_currency(readCell(rowNum, "currency"));
        testData.setJournal_conversion_type(readCell(rowNum, "conversion_rate_type"));
        testData.setJournal_conversion_rate(readCell(rowNum, "conversion_rate"));
        testData.setJournal_line1_account(readCell(rowNum, "journal_line1_account"));
        testData.setJournal_line1_debit(readCell(rowNum, "journal_line1_debit"));
        testData.setJournal_line1_desc(readCell(rowNum, "journal_line1_desc"));
        testData.setJournal_line2_account(readCell(rowNum, "journal_line2_account"));
        testData.setJournal_line2_credit(readCell(rowNum, "journal_line2_credit"));
        testData.setJournal_line2_desc(readCell(rowNum, "journal_line2_desc"));
    }
*/

}
