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
        ReportUtils.initExtentReports();
    }

    @AfterAll
    public static void afterAll() {
        ReportUtils.flushReports();
    }

    @Before
    public void beforeScenario(Scenario scenario) {
        String safeName = scenario.getName().replaceAll("[^a-zA-Z0-9-_.]", "_");
        ThreadContext.put("threadName", safeName);

        TestContext context = TestContextManager.getContext();
        TestData testData = context.getTestData();

        // Set scenario info
        context.setScenario(scenario);
        testData.setScenarioName(scenario.getName());
        context.clearPageCache(); // fresh pages per scenario
        ReportUtils.createScenarioTest(scenario.getName());

        logger.info("Starting scenario: {}", testData.getScenarioName());
        try {
            // Open Excel workbook for this scenario/thread
            XSSFWorkbook workbook = ExcelUtils.openWorkbook(testData.getTestDataFile());
            XSSFSheet sheet = ExcelUtils.getSheet(workbook, testData.getTestDataSheet());

            // Store in TestData thread-local
            testData.setWorkBook(workbook);
            testData.setWorkSheet(sheet);

            // Initialize ExcelSteps thread-local
            testData.getExcel(); // ensures ExcelSteps is ready

        } catch (Exception e) {
            logger.error("Error initializing Excel workbook for scenario: {}", scenario.getName(), e);
            throw new RuntimeException(e);
        }
    }


    @After
    public void afterScenario(Scenario scenario) {
        TestContext context = TestContextManager.getContext();
        TestData testData = context.getTestData();
        // Save Excel workbook for this scenario
        try (XSSFWorkbook workbook = testData.getWorkBook()) {

            if (workbook != null) {
                testData.getExcel().save(); // save workbook via ExcelSteps
                testData.clearThreadExcel(); // clear thread-local to prevent leaks
            }
        } catch (Exception e) {
            logger.error("Error saving Excel workbook for scenario: {}", scenario.getName(), e);
        }
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

}



