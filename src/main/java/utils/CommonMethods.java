package utils;

import context.TestContextManager;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;

import static utils.Constants.*;


@RequiredArgsConstructor
public class CommonMethods {

    private static final Logger logger = LogManager.getLogger(CommonMethods.class);


    protected final WebDriver driver;
    protected final TestData testData;


    // Convenience method to get any page by class
    public <T> T getPage(Class<T> pageClass) {
        return TestContextManager.getContext().getPageManager().getPage(pageClass);
    }

    public void logPageAction(String actionDescription) {
        ReportUtils.logStepWithScreenshot(actionDescription, driver);
    }

    public void waitForVisibilityOfElement(By locator) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
        wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public void waitForElementToBeVisibleAndClickable(By locator) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
        wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    public void waitForElementToBeClickable(By locator) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
        wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    @FunctionalInterface
    private interface ElementAction {
        void perform(WebElement element);
    }

    private enum ActionType { CLICK, INPUT }

    // Custom exception for clarity
    public static class ElementActionFailedException extends RuntimeException {
        public ElementActionFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private void retryAction(By locator, ElementAction action, ActionType actionType) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
                ExpectedCondition<WebElement> condition = getConditionForAction(actionType, locator);

                WebElement element = wait.until(ExpectedConditions.refreshed(condition));
                action.perform(element);

                logger.info("{} action succeeded on attempt {} for element {}", actionType, attempt, locator);
                return; // Success, exit method

            } catch (StaleElementReferenceException | TimeoutException | ElementNotInteractableException e) {
                lastException = e;

                logger.warn("{} on element {}, attempt {}/{}", e.getClass().getSimpleName(), locator, attempt, MAX_RETRIES);

                if (attempt < MAX_RETRIES) {
                    long sleepTime = RETRY_DELAY_MS * attempt; // incremental backoff
                    logger.info("Retrying after {} ms", sleepTime);
                    sleep(sleepTime);
                }
            } catch (Exception e) {
                throw new ElementActionFailedException(
                        "Unexpected error during " + actionType + " on element: " + locator, e);
            }
        }

        // If all retries fail
        throw new ElementActionFailedException(
                "Failed to perform " + actionType + " on element: " + locator +
                        " after " + MAX_RETRIES + " attempts", lastException);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Sleep interrupted", e);
        }
    }

    private ExpectedCondition<WebElement> getConditionForAction(ActionType actionType, By locator) {
        return switch (actionType) {
            case CLICK -> ExpectedConditions.elementToBeClickable(locator);
            case INPUT -> new ExpectedCondition<>() {
                @Override
                public WebElement apply(WebDriver driver) {
                    WebElement element = ExpectedConditions.visibilityOfElementLocated(locator).apply(driver);
                    if (element != null && element.isEnabled()) {
                        return element;
                    }
                    return null;
                }

                @Override
                public String toString() {
                    return "visibility and enabled state of element located by: " + locator;
                }
            };

        };
    }

    public void inputText(By locator, String text) {
        inputText(locator, text, false);
    }

    public void inputText(By locator, String text, boolean pressTab) {
        retryAction(locator, element -> {
            element.clear();
            element.sendKeys(text);
            if (pressTab) element.sendKeys(Keys.TAB);
            logger.info("Input text '{}' into element located by {}", text, locator);
        }, ActionType.INPUT);
    }

    public void clickElement(By locator) {
        retryAction(locator, element -> {
            element.click();
            logger.info("Clicked element located by {}", locator);
        }, ActionType.CLICK);
    }

    public void scrollThroughPageToTriggerLazyLoading() {
        try {
            logger.debug("Starting to scroll through the page to trigger lazy loading");

            long lastHeight = getScrollHeight();
            long viewportHeight = getViewportHeight();
            long currentScroll = 0;

            while (true) {
                // Scroll down by half-viewport (absolute positioning)
                currentScroll += viewportHeight / 2;
                ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, arguments[0]);", currentScroll);

                // Use your dedicated sleep method
                sleep(500);

                long newHeight = getScrollHeight();
                if (newHeight == lastHeight) {
                    // No new content loaded, exit loop
                    break;
                }
                lastHeight = newHeight;

                // Update viewport height in case it changed
                viewportHeight = getViewportHeight();
            }

            // Ensure the final scroll reaches bottom
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
            sleep(500);
            logger.debug("Scrolled to the bottom of the page");

            // Scroll back to top
            scrollBackToTop(lastHeight, viewportHeight);

            logger.debug("Completed lazy loading scroll sequence");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lazy loading scroll interrupted", e);
        } catch (Exception e) {
            logger.error("Failed to scroll through page for lazy loading: {}", e.getMessage(), e);
            throw new RuntimeException("Lazy loading scroll failed", e);
        }
    }

    private void scrollBackToTop(long scrollHeight, long viewportHeight) throws InterruptedException {
        long scrollBack = scrollHeight;
        while (scrollBack > 0) {
            scrollBack -= viewportHeight / 2;
            ((JavascriptExecutor) driver)
                    .executeScript("window.scrollTo(0, arguments[0]);", Math.max(scrollBack, 0));
            sleep(200); // Use your sleep method
        }
        logger.debug("Completed scrolling back to the top of the page");
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
    public void waitForPageToLoad(String pageName) {
        try {
            logger.debug("Waiting for page to load: {}", pageName);

            // Wait for DOM ready state
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getPageLoadTimeout()));
            wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

            // Optional: Wait for jQuery AJAX to finish
            waitForJQueryInactive();

            logger.info("Page loaded successfully: {}", pageName);

        } catch (TimeoutException e) {
            String errorMsg = String.format("Page '%s' failed to load within %d seconds",
                    pageName, testData.getPageLoadTimeout());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);

        } catch (Exception e) {
            String errorMsg = String.format("Page '%s' did not load properly", pageName);
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    private void waitForJQueryInactive() {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
            shortWait.until(d -> {
                Long jQueryActive = (Long) ((JavascriptExecutor) d)
                        .executeScript("return window.jQuery ? jQuery.active : 0");
                return jQueryActive != null && jQueryActive == 0;
            });
        } catch (Exception e) {
            // jQuery doesn't present or not active - this is acceptable
            logger.debug("jQuery not present or wait not needed: {}", e.getMessage());
        }
    }

}
