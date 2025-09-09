package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Utility class for scrolling and triggering lazy loading.
 */
public class ScrollUtils {

    private static final Logger logger = LogManager.getLogger(ScrollUtils.class);

    private final WebDriver driver;

    public ScrollUtils(WebDriver driver) {
        this.driver = driver;
    }

    public void scrollThroughPageToTriggerLazyLoading() {
        logger.debug("Starting to scroll through the page to trigger lazy loading");

        long lastHeight = getScrollHeight();
        long viewportHeight = getViewportHeight();
        long currentScroll = 0;

        while (true) {
            currentScroll += viewportHeight / 2;
            executeScroll(currentScroll);
            sleep(500);

            long newHeight = getScrollHeight();
            if (newHeight == lastHeight) break;

            lastHeight = newHeight;
            viewportHeight = getViewportHeight();
        }

        // Scroll to the bottom to ensure all content is loaded
        executeScroll(getScrollHeight());
        sleep(500);
        logger.debug("Scrolled to the bottom of the page");

        // Scroll back to top
        try {
            scrollBackToTop(lastHeight, viewportHeight);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lazy loading scroll interrupted", e);
        }

        logger.debug("Completed lazy loading scroll sequence");
    }

    private void scrollBackToTop(long scrollHeight, long viewportHeight) throws InterruptedException {
        long scrollBack = scrollHeight;
        while (scrollBack > 0) {
            scrollBack -= viewportHeight / 2;
            executeScroll(Math.max(scrollBack, 0));
            sleep(200);
        }
        logger.debug("Completed scrolling back to the top of the page");
    }

    private void executeScroll(long y) {
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, arguments[0]);", y);
    }

    private long getScrollHeight() {
        Number scrollHeightValue = (Number) ((JavascriptExecutor) driver)
                .executeScript("return document.body.scrollHeight");
        return scrollHeightValue != null ? scrollHeightValue.longValue() : 0;
    }

    private long getViewportHeight() {
        Number viewportHeightValue = (Number) ((JavascriptExecutor) driver)
                .executeScript("return window.innerHeight");
        return viewportHeightValue != null ? viewportHeightValue.longValue() : 0;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Sleep interrupted", e);
        }
    }
}
