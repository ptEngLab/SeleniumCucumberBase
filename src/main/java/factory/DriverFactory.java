package factory;

import config.ConfigReader;
import lombok.Getter;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import utils.TestData;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DriverFactory {

    private static final Logger logger = LogManager.getLogger(DriverFactory.class);

    private static final ThreadLocal<WebDriver> tlDriver = new ThreadLocal<>();

    @Getter
    private final TestData testData;

    private final ConfigReader config;

    public DriverFactory() {
        this.testData = new TestData();
        this.config = ConfigReader.getInstance();
        initialSetup();
    }

    private void initialSetup() {
        testData.setAppUrl(config.getString("application_url", "http://localhost"));
        testData.setBrowserName(config.getString("browser", "edge").toLowerCase());
        testData.setHeadless(config.getBoolean("headless", false));
        testData.setTeamName(config.getString("team_name", "QA"));

        String testDataFile = config.getString("testDataFile", "testData.xlsx");
        testData.setTestDataFile(System.getProperty("user.dir") + File.separator + testDataFile);
        testData.setTestDataSheet(config.getString("testDataSheetName", "Sheet1"));
        testData.setCredentialsSheet(config.getString("credentialsSheetName", "Credentials"));
        testData.setPageLoadTimeout(config.getInt("page_load_timeout", 30));
        testData.setImplicitWait(config.getInt("implicit_wait", 10));
        testData.setExplicitWait(config.getInt("explicit_wait", 20));

        testData.setReportConfigPath(config.getString("reportsDir", "reports"));
        logger.info("Application URL: {}", testData.getAppUrl());
        logger.info("Test Data File: {}", testData.getTestDataFile());
        logger.info("Test Data Sheet: {}", testData.getTestDataSheet());
        logger.info("Team Name: {}", testData.getTeamName());
        logger.info("Browser: {}, Headless: {}", testData.getBrowserName(), testData.isHeadless());
    }

    public WebDriver initDriver() {

        String browser = testData.getBrowserName();
        boolean headless = testData.isHeadless();
        String windowSize = config.getString("window_size", "1920,1080");
        String driverPath = System.getProperty("user.dir") + File.separator + config.getString("driver_path", "drivers");

        logger.info("Initializing {} driver in {} mode", browser, headless ? "headless" : "UI");

        WebDriver driver;

        driver = switch (browser) {
            case "chrome" -> new ChromeDriver(configureChrome(driverPath, headless, windowSize));
            case "edge" -> new EdgeDriver(configureEdge(driverPath, headless, windowSize));
            case "firefox" -> new FirefoxDriver(configureFirefox(driverPath, headless, windowSize));
            default -> throw new IllegalArgumentException("Unsupported browser: " + browser);
        };


        // Common WebDriver setup
        driver.manage().deleteAllCookies();
        if (!headless) driver.manage().window().maximize();
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(testData.getPageLoadTimeout()));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(testData.getImplicitWait()));

        tlDriver.set(driver);
        return tlDriver.get();
    }

    // ---------------- Chrome ----------------
    private ChromeOptions configureChrome(String driverPath, boolean headless, String windowSize) {
        System.setProperty("webdriver.chrome.driver", driverPath + File.separator + getChromeDriverName());
        ChromeOptions options = new ChromeOptions();
        if (headless) options.addArguments("--headless=new", "--disable-gpu", getWindowSizeArg(windowSize));
        return options;
    }

    // ---------------- Edge ----------------
    private EdgeOptions configureEdge(String driverPath, boolean headless, String windowSize) {
        System.setProperty("webdriver.edge.driver", driverPath + File.separator + getEdgeDriverName());
        EdgeOptions options = new EdgeOptions();
        if (headless) options.addArguments("--headless=new", "--disable-gpu", getWindowSizeArg(windowSize));
        return options;
    }

    // ---------------- Firefox ----------------
    private FirefoxOptions configureFirefox(String driverPath, boolean headless, String windowSize) {
        System.setProperty("webdriver.gecko.driver", driverPath + File.separator + getFirefoxDriverName());
        FirefoxOptions options = new FirefoxOptions();
        if (headless) options.addArguments("--headless", getWindowSizeArg(windowSize));
        return options;
    }

    private String getWindowSizeArg(String windowSize) {
        String[] dims = windowSize.split(",");
        return String.format("--window-size=%s,%s", dims[0].trim(), dims[1].trim());
    }

    // Driver file names
    private String getChromeDriverName() { return isWindows() ? "chromedriver.exe" : "chromedriver"; }
    private String getFirefoxDriverName() { return isWindows() ? "geckodriver.exe" : "geckodriver"; }
    private String getEdgeDriverName() { return isWindows() ? "msedgedriver.exe" : "msedgedriver"; }
    private boolean isWindows() { return System.getProperty("os.name").toLowerCase().contains("win"); }

    // Thread-safe WebDriver getter
    public static WebDriver getDriver() { return tlDriver.get(); }

    public void quitDriver() {
        WebDriver driver = tlDriver.get();
        try {
            if (driver != null) driver.quit();
        } catch (Exception e) {
            logger.error("Error quitting WebDriver: {}", e.getMessage(), e);
        } finally {
            tlDriver.remove();
        }
    }

    // Screenshot capture with timestamp
    public void captureScreenshot(String fileName) {
        WebDriver driver = getDriver();
        if (driver == null) return;

        String reportsDirPath = System.getProperty("user.dir") + File.separator +
                config.getString("screenshot_path", "screenshots") + File.separator;
        File reportsDir = new File(reportsDirPath);
        if (!reportsDir.exists() && !reportsDir.mkdirs()) {
            logger.error("Failed to create screenshot directory: {}", reportsDir.getAbsolutePath());
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File dest = new File(reportsDirPath + fileName + "_" + timestamp + ".png");
        File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

        try {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Screenshot saved: {}", dest.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to save screenshot: {}", e.getMessage(), e);
        }
    }
}
