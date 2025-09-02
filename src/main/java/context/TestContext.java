package context;

import factory.DriverFactory;
import io.cucumber.java.Scenario;
import lombok.Getter;
import lombok.Setter;
import utils.TestData;
import org.openqa.selenium.WebDriver;

@Getter
public class TestContext {

    private final DriverFactory driverFactory;
    private final TestData testData;

    private WebDriver driver;
    private PageManager pageManager;

    @Setter
    private Scenario scenario;

    public TestContext() {
        this.driverFactory = new DriverFactory();
        this.testData = driverFactory.getTestData();
    }

    public WebDriver getDriver() {
        if (driver == null) {
            driver = driverFactory.initDriver();
        }
        return driver;
    }
    public void quitDriver() {
        driverFactory.quitDriver();
        driver = null; // clear reference
    }


    public PageManager getPageManager() {
        if (pageManager == null) {
            pageManager = new PageManager(getDriver(), testData);
        }
        return pageManager;
    }

    public void clearPageCache() {
        if (pageManager != null) {
            pageManager.clearPageCache();
            pageManager = null;
        }
    }

}
