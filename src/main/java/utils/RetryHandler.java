package utils;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;

import static utils.Constants.*;

/**
 * Handles retries for Selenium element actions with configurable conditions.
 */
public class RetryHandler {

    private static final Logger logger = LogManager.getLogger(RetryHandler.class);

    private final WebDriver driver;
    private final TestData testData;

    public RetryHandler(WebDriver driver, TestData testData) {
        this.driver = driver;
        this.testData = testData;
    }

    @FunctionalInterface
    public interface ElementAction {
        void perform(WebElement element);
    }

    public enum ActionType {
        CLICK, INPUT, JS_CLICK, READ, TEXT_MATCH, ATTRIBUTE_MATCH, ATTRIBUTE_NON_EMPTY, TEXT_NON_EMPTY
    }

    public static class ElementActionFailedException extends RuntimeException {
        public ElementActionFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Options for retrying actions.
     */
    @Getter
    public static class RetryOptions {
        private final String expectedText;
        private final String attributeName;

        private RetryOptions(String expectedText, String attributeName) {
            this.expectedText = expectedText;
            this.attributeName = attributeName;
        }

        public static RetryOptions none() {
            return new RetryOptions(null, null);
        }

        public static RetryOptions expectedText(String expectedText) {
            return new RetryOptions(expectedText, null);
        }

        public static RetryOptions attribute(String attributeName) {
            return new RetryOptions(null, attributeName);
        }

        public static RetryOptions attributeMatch(String expectedText, String attributeName) {
            return new RetryOptions(expectedText, attributeName);
        }

    }

    /**
     * Main retry loop for element actions.
     */
    public void retryAction(By locator, ElementAction action, ActionType actionType, RetryOptions options) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
                ExpectedCondition<WebElement> condition =
                        getConditionForAction(actionType, locator, options.getExpectedText(), options.getAttributeName());

                WebElement element = wait.until(ExpectedConditions.refreshed(condition));
                action.perform(element);

                // ✅ Validation for INPUT (ensure value matches)
                if (actionType == ActionType.INPUT && options.getExpectedText() != null) {
                    String attr = options.getAttributeName() != null ? options.getAttributeName() : "value";
                    new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()))
                            .until(ExpectedConditions.attributeToBe(locator, attr, options.getExpectedText()));
                    logger.info("Attribute '{}' successfully matched value '{}' for element {}", attr, options.getExpectedText(), locator);
                }

                logger.info("{} action succeeded on attempt {} for element {}", actionType, attempt, locator);
                return;

            } catch (ElementNotInteractableException e) {
                lastException = e;
                logger.warn("ElementNotInteractableException on element {}, attempt {}/{}", locator, attempt, MAX_RETRIES);

                // ✅ JS Fallback for INPUT
                if (actionType == ActionType.INPUT && options.getExpectedText() != null) {
                    try {
                        WebElement element = driver.findElement(locator);
                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].scrollIntoView(true);" +
                                        "arguments[0].value = arguments[1];" +
                                        "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));",
                                element, options.getExpectedText()
                        );
                        logger.info("JS fallback succeeded for input '{}' into element {}", options.getExpectedText(), locator);
                        return; // ✅ Exit since JS fallback succeeded
                    } catch (Exception jsEx) {
                        logger.error("JS fallback also failed for {}: {}", locator, jsEx.getMessage());
                    }
                }

                if (attempt < MAX_RETRIES) {
                    long sleepTime = RETRY_DELAY_MS * attempt;
                    logger.info("Retrying after {} ms", sleepTime);
                    sleep(sleepTime);
                }

            } catch (StaleElementReferenceException | TimeoutException e) {
                lastException = e;
                logger.warn("{} on element {}, attempt {}/{}", e.getClass().getSimpleName(), locator, attempt, MAX_RETRIES);

                if (attempt < MAX_RETRIES) {
                    long sleepTime = RETRY_DELAY_MS * attempt;
                    logger.info("Retrying after {} ms", sleepTime);
                    sleep(sleepTime);
                }

            } catch (Exception e) {
                throw new ElementActionFailedException(
                        "Unexpected error during " + actionType + " on element: " + locator, e);
            }
        }

        throw new ElementActionFailedException(
                "Failed to perform " + actionType + " on element: " + locator +
                        " after " + MAX_RETRIES + " attempts", lastException);
    }

    private ExpectedCondition<WebElement> getConditionForAction(ActionType actionType, By locator,
                                                                String expectedText, String attributeName) {
        return switch (actionType) {
            case CLICK -> ExpectedConditions.elementToBeClickable(locator);
            case INPUT -> createInputCondition(locator);
            case JS_CLICK -> driver -> {
                WebElement element = ExpectedConditions.presenceOfElementLocated(locator).apply(driver);
                return (element != null && element.isDisplayed()) ? element : null;
            };
            case READ -> ExpectedConditions.presenceOfElementLocated(locator);
            case TEXT_MATCH -> driver -> {
                WebElement element = driver.findElement(locator);
                String actualText = element.getText();
                return matchesExpectedText(actualText, expectedText) ? element : null;
            };
            case ATTRIBUTE_MATCH -> driver -> {
                WebElement element = driver.findElement(locator);
                String attrValue = element.getAttribute(attributeName);
                return matchesExpectedText(attrValue, expectedText) ? element : null;
            };
            case ATTRIBUTE_NON_EMPTY -> driver -> {
                WebElement element = driver.findElement(locator);
                String attrValue = element.getAttribute(attributeName);
                return (StringUtils.isNotBlank(attrValue)) ? element : null;
            };
            case TEXT_NON_EMPTY -> driver -> {
                WebElement element = driver.findElement(locator);
                String text = element.getText();
                return !text.trim().isEmpty() ? element : null;
            };

        };
    }

    private ExpectedCondition<WebElement> createInputCondition(By locator) {
        return new ExpectedCondition<>() {
            @Override
            public WebElement apply(WebDriver driver) {
                WebElement element = ExpectedConditions.presenceOfElementLocated(locator).apply(driver);
                if (element == null) {
                    logger.info("Element not present yet: {}", locator);
                    return null;
                }
                try {
                    String display = element.getCssValue("display");
                    String visibility = element.getCssValue("visibility");
                    String opacity = element.getCssValue("opacity");
                    logger.info("Element CSS - display: {}, visibility: {}, opacity: {}", display, visibility, opacity);
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);

                    Boolean visible = (Boolean) ((JavascriptExecutor) driver)
                            .executeScript(
                                    "var elem = arguments[0]; " +
                                            "var rect = elem.getBoundingClientRect(); " +
                                            "return (rect.width > 0 && rect.height > 0) && " +
                                            "window.getComputedStyle(elem).visibility === 'visible' && " +
                                            "window.getComputedStyle(elem).display !== 'none';",
                                    element);

                    if (Boolean.FALSE.equals(visible)) {
                        logger.info("Element is present but not visible yet according to JS: {}", locator);
                        return null;
                    }
                } catch (Exception e) {
                    logger.warn("JS visibility check failed for element {}: {}", locator, e.getMessage());
                }
                if (!element.isDisplayed()) {
                    logger.info("Element is present but not displayed yet: {}", locator);
                    return null;
                }
                if (!element.isEnabled()) {
                    logger.info("Element is visible but not enabled yet: {}", locator);
                    return null;
                }
                return element;
            }

            @Override
            public String toString() {
                return "visibility and enabled state of element located by: " + locator;
            }
        };
    }

    private boolean matchesExpectedText(String actualText, String expectedText) {
        if (StringUtils.isBlank(expectedText)) return StringUtils.isNotBlank(actualText);
        if (expectedText.startsWith("regex:")) {
            String pattern = expectedText.substring(6).trim();
            return StringUtils.isNotBlank(actualText) && actualText.trim().matches(pattern);
        }
        return StringUtils.isNotBlank(actualText) && actualText.contains(expectedText);
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