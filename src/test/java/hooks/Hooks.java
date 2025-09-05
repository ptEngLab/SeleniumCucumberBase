package hooks;

import context.TestContext;
import context.TestContextManager;
import io.cucumber.java.*;
import org.apache.logging.log4j.ThreadContext;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import utils.CommonMethods;
import utils.ExcelUtils;
import utils.ReportUtils;
import utils.TestData;

import static utils.ReportUtils.attachScreenshot;

public class Hooks {

    private static final Logger logger = LogManager.getLogger(Hooks.class);


    @BeforeAll
    public static void beforeAll() {
        ReportUtils.initExtentReports(TestContextManager.getContext().getTestData());
    }

    @AfterAll
    public static void afterAll() {
        ReportUtils.flushReports();
    }

    @Before
    public void beforeScenario(Scenario scenario) {
        String scenarioName = scenario.getName();
        String safeName = scenarioName.replaceAll("[^a-zA-Z0-9-_.]", "_");
        ThreadContext.put("threadName", safeName);

        TestContext context = TestContextManager.getContext();
        TestData testData = context.getTestData();

        // Set scenario info
        context.setScenario(scenario);
        testData.setScenarioName(scenarioName);

        initExcelForScenario(testData, scenario);

        testData.getExcel().loadScenarioData(scenarioName);

        context.clearPageCache(); // fresh pages per scenario
        ReportUtils.createScenarioTest(testData);

        logger.info("Starting scenario: {}", testData.getScenarioName());

    }


    @After
    public void afterScenario(Scenario scenario) {
        TestContext context = TestContextManager.getContext();
        TestData testData = context.getTestData();
        // Save Excel workbook for this scenario
        saveAndCloseExcel(testData, scenario);
        // Quit driver and clean up
        context.quitDriver();
        TestContextManager.removeContext();
        ReportUtils.removeTest();
        ThreadContext.clearMap();

    }

    @AfterStep
    public void afterStep(Scenario scenario) {
        TestContext context = TestContextManager.getContext();
        CommonMethods commonMethods = new CommonMethods(context.getDriver(), context.getTestData());
        commonMethods.scrollThroughPageToTriggerLazyLoading();
        try {
            attachScreenshot(scenario, context.getDriver());
        } catch (Exception e) {
            logger.warn("Screenshot capture failed: {}", e.getMessage());
        }
    }

    private void initExcelForScenario(TestData testData, Scenario scenario) {
        try {

            // Open Excel workbook for this scenario/thread
            XSSFWorkbook workbook = ExcelUtils.openWorkbook(testData.getTestDataFile());
            XSSFSheet sheet = ExcelUtils.getSheet(workbook, testData.getTestDataSheet());

            // Store in TestData thread-local
            testData.setWorkBook(workbook);
            testData.setWorkSheet(sheet);

            // Initialize ExcelSteps for thread-local access
            testData.getExcel();

            logger.info("Excel initialized for scenario: {}", scenario.getName());
        } catch (Exception e) {
            logger.error("Error initializing Excel for scenario: {}", scenario.getName(), e);
            throw new RuntimeException(e);
        }
    }


    private void saveAndCloseExcel(TestData testData, Scenario scenario) {
        XSSFWorkbook workbook = testData.getWorkBook();
        if (workbook != null) {
            try (workbook) {
                try {
                    // Save workbook via ExcelSteps
                    testData.getExcel().save();
                } catch (Exception e) {
                    logger.error("Error saving Excel for scenario: {}", scenario.getName(), e);
                }
            } catch (Exception e) {
                logger.warn("Failed to close Excel workbook for scenario: {}", scenario.getName());
            } finally {
                testData.clearThreadExcel(); // Prevent memory leaks
            }
        }
    }

}



