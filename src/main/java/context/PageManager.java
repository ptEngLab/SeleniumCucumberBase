package context;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
import pages.HomePage;
import pages.LoginPage;
import utils.TestData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

@Getter
@RequiredArgsConstructor
public class PageManager {

    private static final Logger logger = LogManager.getLogger(PageManager.class);

    private final WebDriver driver;
    private final TestData testData;
    private final ConcurrentHashMap<Class<?>, Object> pageCache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T getPage(Class<T> pageClass) {
        return (T) pageCache.computeIfAbsent(pageClass, clazz -> {
            try {
                logger.debug("Creating page instance: {}", clazz.getSimpleName());
                return clazz.getConstructor(WebDriver.class, TestData.class)
                        .newInstance(driver, testData);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Page class must have (WebDriver, TestData) constructor: " +
                        clazz.getName(), e);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create page: " + clazz.getName(), e);
            }
        });
    }


    public void clearPageCache() {
        pageCache.clear();
        logger.info("Page cache cleared");
    }

    // Helper method for common pages
    public LoginPage getLoginPage() {
        return getPage(LoginPage.class);
    }

    public HomePage getHomePage() {
        return getPage(HomePage.class);
    }
}