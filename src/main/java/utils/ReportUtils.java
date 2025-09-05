package utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import io.cucumber.java.Scenario;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class ReportUtils {

    private static final Logger logger = LogManager.getLogger(ReportUtils.class);
    private static ExtentReports extentReports;
    private static final ThreadLocal<ExtentTest> tlTest = new ThreadLocal<>();

    public static void initExtentReports(TestData testData) {
        if (extentReports == null) {
            String reportPath = System.getProperty("user.dir") + File.separator + testData.getReportConfigPath();
            String teamName = testData.getTeamName();
            String browser = testData.getBrowserName();

            File dir = new File(reportPath);
            if (!dir.exists() && !dir.mkdirs()) {
                logger.error("Failed to create report directory: {}", dir.getAbsolutePath());
            }

            logger.info("Extent report will be generated at: {}", reportPath + File.separator + "ExtentReport.html");

            ExtentSparkReporter spark = new ExtentSparkReporter(reportPath + File.separator + "ExtentReport.html");
            spark.config().setTheme(Theme.STANDARD);
            spark.config().setReportName("Automation Test Report");
            spark.config().setDocumentTitle("Selenium Cucumber Report");

            extentReports = new ExtentReports();
            extentReports.attachReporter(spark);
            extentReports.setSystemInfo("OS", System.getProperty("os.name"));
            extentReports.setSystemInfo("Java Version", System.getProperty("java.version"));
            extentReports.setSystemInfo("Team Name", teamName);
            extentReports.setSystemInfo("Browser", browser);
        }
    }

    public static synchronized void createScenarioTest(TestData testData) {
        if (extentReports == null) initExtentReports(testData);
        ExtentTest test = extentReports.createTest(testData.getScenarioName());
        tlTest.set(test);
    }

    public static ExtentTest getTest() { return tlTest.get(); }
    public static void removeTest() { tlTest.remove(); }
    public static void flushReports() { if (extentReports != null) extentReports.flush(); }

    public static void attachScreenshot(Scenario scenario, WebDriver driver) {
        byte[] screenshot = (driver instanceof EdgeDriver)
                ? ScreenshotUtils.captureFullPageScreenshotWithCDP((EdgeDriver) driver)
                : ScreenshotUtils.captureStandardScreenshot(driver);

        scenario.attach(screenshot, "image/png", scenario.getName().replaceAll(" ", "_"));
        String html = "<img src='data:image/png;base64," +
                java.util.Base64.getEncoder().encodeToString(screenshot) + "' width='600'/>";

        if (scenario.isFailed()) getTest().fail(html);
        else getTest().pass(html);
    }

    public static void logStepWithScreenshot(String stepDescription, WebDriver driver) {
        byte[] screenshot = (driver instanceof EdgeDriver)
                ? ScreenshotUtils.captureFullPageScreenshotWithCDP((EdgeDriver) driver)
                : ScreenshotUtils.captureStandardScreenshot(driver);

        String html = "<img src='data:image/png;base64," +
                java.util.Base64.getEncoder().encodeToString(screenshot) + "' width='600'/>";
        getTest().log(Status.INFO, stepDescription + "<br>" + html);
    }

    public static void logStep(String message) {
        ExtentTest test = getTest();
        if (test != null) {
            test.log(Status.INFO, message);
        }
    }
}
