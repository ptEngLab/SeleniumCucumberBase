package context;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
import pages.LoginPage;
import utils.ExcelSteps;
import utils.TestData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

import static utils.Constants.PASSWORD;
import static utils.Constants.USERNAME;

@Getter
@RequiredArgsConstructor
public class PageManager {

    private static final Logger logger = LogManager.getLogger(PageManager.class);

    private final WebDriver driver;
    private final TestData testData;
    private final ConcurrentHashMap<Class<?>, Object> pageCache = new ConcurrentHashMap<>();

    /**
     * Get or create a Page Object instance with caching.
     * Requires page class to have (WebDriver, TestData) constructor.
     */
    @SuppressWarnings("unchecked")
    public <T> T getPage(Class<T> pageClass) {
        return (T) pageCache.computeIfAbsent(pageClass, this::createPageInstance);
    }

    private <T> Object createPageInstance(Class<T> pageClass) {
        try {
            logger.debug("Creating page instance: {}", pageClass.getSimpleName());
            return pageClass.getConstructor(WebDriver.class, TestData.class)
                    .newInstance(driver, testData);
        } catch (NoSuchMethodException e) {
            String msg = String.format("Page class '%s' must have a constructor (WebDriver, TestData)", pageClass.getName());
            logger.error(msg, e);
            throw new IllegalArgumentException(msg, e);
        } catch (Exception e) {
            String msg = String.format("Failed to create page: %s", pageClass.getName());
            logger.error(msg, e);
            throw new IllegalStateException(msg, e);
        }
    }

    /** Clears the page cache to avoid stale objects. */
    public void clearPageCache() {
        pageCache.clear();
        logger.info("Page cache cleared");
    }

    /**
     * Logs in using credentials for a specific role.
     * Reads data from Excel based on the role.
     */
    public void loginAsRole(String role) {
        TestContext context = TestContextManager.getContext();
        ExcelSteps excel = context.getTestData().getExcel();
        LoginPage loginPage = getPage(LoginPage.class);
        String credentialsSheet = testData.getCredentialsSheet();

        int row = excel.getRowNum(credentialsSheet, role);
        if (row < 0) {
            String msg = "Role not found in credentials sheet: " + role;
            logger.error(msg);
            throw new IllegalArgumentException(msg);
        }

        String username = excel.readLoginCell(credentialsSheet, row, USERNAME);
        String password = excel.readLoginCell(credentialsSheet, row, PASSWORD);

        logger.info("Logging in as role: {}", role);
        loginPage.login(username, password);
    }
}