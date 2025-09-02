package utils;

import lombok.Getter;
import lombok.Setter;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Getter
@Setter
public class TestData {

    private String scenarioName;
    private String appUrl;
    private String browserName;
    private boolean headless;
    private String teamName;
    private String testDataFile;
    private String testDataSheet;

    private int implicitWait;
    private int explicitWait;
    private int pageLoadTimeout;

    private String reportConfigPath;
    private String screenshotPath;

    private String applicationUsername;
    private String applicationPassword;

    private String journalProcessID;
    private String journal_batch_name;
    private String journal_batch_description;
    private String accounting_period;
    private String reversal_method;
    private String accounting_source;
    private String batch_status;
    private String attachment_text;
    private String attachment_file_name;
    private String journal_name;
    private String journal_desc;
    private String journal_ledger;
    private String journal_currency;
    private String journal_category;
    private String journal_conversion_rate;
    private String journal_conversion_type;
    private String journal_line1_account;
    private String journal_line1_debit;
    private String journal_line1_credit;
    private String journal_line1_desc;
    private String journal_line2_account;
    private String journal_line2_debit;
    private String journal_line2_credit;
    private String journal_line2_desc;


    // Thread-local Excel workbook and sheet for parallel execution
    private final ThreadLocal<XSSFWorkbook> threadWorkbook = new ThreadLocal<>();
    private final ThreadLocal<XSSFSheet> threadWorksheet = new ThreadLocal<>();

    // Thread-local ExcelSteps instance
    private final ThreadLocal<ExcelSteps> threadExcel = new ThreadLocal<>();

    public XSSFWorkbook getWorkBook() {
        return threadWorkbook.get();
    }

    public void setWorkBook(XSSFWorkbook workbook) {
        threadWorkbook.set(workbook);
    }

    public XSSFSheet getWorkSheet() {
        return threadWorksheet.get();
    }

    public void setWorkSheet(XSSFSheet sheet) {
        threadWorksheet.set(sheet);
    }

    public ExcelSteps getExcel() {
        if (threadExcel.get() == null) {
            threadExcel.set(new ExcelSteps(this));
        }
        return threadExcel.get();
    }

    public void clearThreadExcel() {
        threadWorkbook.remove();
        threadWorksheet.remove();
        threadExcel.remove();
    }
}
