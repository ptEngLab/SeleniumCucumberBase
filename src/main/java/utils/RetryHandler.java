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
import java.util.function.Function;

import static utils.Constants.*;

/**
 * Handles retries for Selenium element actions with configurable conditions.
 * Enhanced with exponential backoff, jitter, post-validation hooks, and extended JS fallbacks.
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
        private final Integer maxRetries;
        private final Long retryDelayMs;
        private final Boolean useExponentialBackoff;
        private final Boolean useJitter;
        private final Function<WebDriver, Boolean> postValidation;

        private RetryOptions(String expectedText, String attributeName, Integer maxRetries,
                             Long retryDelayMs, Boolean useExponentialBackoff, Boolean useJitter,
                             Function<WebDriver, Boolean> postValidation) {
            this.expectedText = expectedText;
            this.attributeName = attributeName;
            this.maxRetries = maxRetries;
            this.retryDelayMs = retryDelayMs;
            this.useExponentialBackoff = useExponentialBackoff;
            this.useJitter = useJitter;
            this.postValidation = postValidation;
        }

        public static RetryOptions none() {
            return new RetryOptions(null, null, null, null, null, null, null);
        }

        public static RetryOptions expectedText(String expectedText) {
            return new RetryOptions(expectedText, null, null, null, null, null, null);
        }

        public static RetryOptions attribute(String attributeName) {
            return new RetryOptions(null, attributeName, null, null, null, null, null);
        }

        public static RetryOptions attributeMatch(String expectedText, String attributeName) {
            return new RetryOptions(expectedText, attributeName, null, null, null, null, null);
        }

        public static RetryOptionsBuilder builder() {
            return new RetryOptionsBuilder();
        }

        // Builder pattern for advanced configuration
        public static class RetryOptionsBuilder {
            private String expectedText;
            private String attributeName;
            private Integer maxRetries;
            private Long retryDelayMs;
            private Boolean useExponentialBackoff = true;
            private Boolean useJitter = true;
            private Function<WebDriver, Boolean> postValidation;

            public RetryOptionsBuilder expectedText(String expectedText) {
                this.expectedText = expectedText;
                return this;
            }

            public RetryOptionsBuilder attributeName(String attributeName) {
                this.attributeName = attributeName;
                return this;
            }

            public RetryOptionsBuilder maxRetries(Integer maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }

            public RetryOptionsBuilder retryDelayMs(Long retryDelayMs) {
                this.retryDelayMs = retryDelayMs;
                return this;
            }

            public RetryOptionsBuilder useExponentialBackoff(Boolean useExponentialBackoff) {
                this.useExponentialBackoff = useExponentialBackoff;
                return this;
            }

            public RetryOptionsBuilder useJitter(Boolean useJitter) {
                this.useJitter = useJitter;
                return this;
            }

            public RetryOptionsBuilder postValidation(Function<WebDriver, Boolean> postValidation) {
                this.postValidation = postValidation;
                return this;
            }

            public RetryOptions build() {
                return new RetryOptions(expectedText, attributeName, maxRetries,
                        retryDelayMs, useExponentialBackoff, useJitter, postValidation);
            }
        }
    }

    // ---------------------------
    // JS Snippets (centralized)
    // ---------------------------
    private static final String JS_SCROLL_INTO_VIEW =
            "arguments[0].scrollIntoView({behavior:'auto', block:'center', inline:'center'});";

    private static final String JS_CLICK =
            "arguments[0].click();";

    private static final String JS_INPUT =
            "arguments[0].value = arguments[1];" +
                    "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                    "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));";

    // ---------------------------
    // Retry loop
    // ---------------------------
    public void retryAction(By locator, ElementAction action, ActionType actionType, RetryOptions options) {
        Exception lastException = null;
        int maxRetries = getEffectiveMaxRetries(options);
        long baseDelayMs = getEffectiveRetryDelay(options);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
                ExpectedCondition<WebElement> condition =
                        getConditionForAction(actionType, locator, options.getExpectedText(), options.getAttributeName());

                WebElement element = wait.until(ExpectedConditions.refreshed(condition));
                action.perform(element);

                // Validation for INPUT
                if (actionType == ActionType.INPUT && options.getExpectedText() != null) {
                    validateInputAction(locator, options);
                }

                // Post-validation hook
                if (options.getPostValidation() != null) {
                    if (!options.getPostValidation().apply(driver)) {
                        throw new TimeoutException("Post-validation failed for " + locator);
                    }
                }

                logger.info("{} action succeeded on attempt {} for element {}", actionType, attempt, locator);
                return;

            } catch (ElementNotInteractableException e) {
                lastException = e;
                logger.warn("ElementNotInteractableException on element {}, attempt {}/{}", locator, attempt, maxRetries);

                // JS Fallback
                if (attemptJsFallback(locator, actionType, options.getExpectedText())) {
                    driver.findElement(locator);
                    return;
                }

                handleRetryDelay(attempt, baseDelayMs, options, locator.toString());

            } catch (StaleElementReferenceException e) {
                lastException = e;
                logger.warn("StaleElementReferenceException on element {}, attempt {}/{}", locator, attempt, maxRetries);
                sleep(100); // quick retry, no full backoff

            } catch (TimeoutException e) {
                lastException = e;
                logger.warn("TimeoutException on element {}, attempt {}/{}", locator, attempt, maxRetries);
                handleRetryDelay(attempt, baseDelayMs, options, locator.toString());

            } catch (Exception e) {
                throw new ElementActionFailedException(
                        "Unexpected error during " + actionType + " on element: " + locator, e);
            }
        }

        throw new ElementActionFailedException(
                "Failed to perform " + actionType + " on element: " + locator +
                        " after " + maxRetries + " attempts", lastException);
    }

    // ---------------------------
    // Helper methods
    // ---------------------------
    private int getEffectiveMaxRetries(RetryOptions options) {
        return options != null && options.getMaxRetries() != null ? options.getMaxRetries() : MAX_RETRIES;
    }

    private long getEffectiveRetryDelay(RetryOptions options) {
        return options != null && options.getRetryDelayMs() != null ? options.getRetryDelayMs() : RETRY_DELAY_MS;
    }

    private void handleRetryDelay(int attempt, long baseDelayMs, RetryOptions options, String elementInfo) {
        int maxRetries = getEffectiveMaxRetries(options);
        if (attempt < maxRetries) {
            long sleepTime = calculateSleepTime(attempt, baseDelayMs, options);
            logger.debug("Retrying {} after {} ms", elementInfo, sleepTime);
            sleep(sleepTime);
        }
    }

    private long calculateSleepTime(int attempt, long baseDelayMs, RetryOptions options) {
        boolean useExponentialBackoff = options == null || options.getUseExponentialBackoff() == null ||
                options.getUseExponentialBackoff();
        boolean useJitter = options == null || options.getUseJitter() == null || options.getUseJitter();

        long delay = useExponentialBackoff ? baseDelayMs * (long) Math.pow(2, attempt - 1) : baseDelayMs;

        if (useJitter) {
            long jitter = (long) (Math.random() * delay * 0.2);
            delay += jitter;
        }
        return delay;
    }

    private void validateInputAction(By locator, RetryOptions options) {
        String attr = options.getAttributeName() != null ? options.getAttributeName() : "value";
        new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()))
                .until(ExpectedConditions.attributeToBe(locator, attr, options.getExpectedText()));
        logger.info("Attribute '{}' matched expected value '{}' for element {}",
                attr, options.getExpectedText(), locator);
    }

    private boolean attemptJsFallback(By locator, ActionType actionType, String text) {
        try {
            WebElement element = driver.findElement(locator);
            ((JavascriptExecutor) driver).executeScript(JS_SCROLL_INTO_VIEW, element);

            switch (actionType) {
                case CLICK, JS_CLICK -> ((JavascriptExecutor) driver).executeScript(JS_CLICK, element);
                case INPUT -> {
                    if (text != null) {
                        ((JavascriptExecutor) driver).executeScript(JS_INPUT, element, text);
                    }
                }
                default -> {
                    logger.warn("No JS fallback implemented for {}", actionType);
                    return false;
                }
            }

            logger.info("JS fallback succeeded for {} on element {}", actionType, locator);
            return true;

        } catch (Exception jsEx) {
            logger.error("JS fallback failed for {}: {}", locator, jsEx.getMessage());
            return false;
        }
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
                return matchesExpectedText(element.getText(), expectedText) ? element : null;
            };
            case ATTRIBUTE_MATCH -> driver -> {
                WebElement element = driver.findElement(locator);
                return matchesExpectedText(element.getAttribute(attributeName), expectedText) ? element : null;
            };
            case ATTRIBUTE_NON_EMPTY -> driver -> {
                WebElement element = driver.findElement(locator);
                return StringUtils.isNotBlank(element.getAttribute(attributeName)) ? element : null;
            };
            case TEXT_NON_EMPTY -> driver -> {
                WebElement element = driver.findElement(locator);
                return StringUtils.isNotBlank(element.getText()) ? element : null;
            };
        };
    }

    private ExpectedCondition<WebElement> createInputCondition(By locator) {
        return new ExpectedCondition<>() {
            @Override
            public WebElement apply(WebDriver driver) {
                WebElement element = ExpectedConditions.presenceOfElementLocated(locator).apply(driver);
                if (element == null) return null;

                try {
                    ((JavascriptExecutor) driver).executeScript(JS_SCROLL_INTO_VIEW, element);
                    Boolean visible = (Boolean) ((JavascriptExecutor) driver).executeScript(
                            "var elem = arguments[0]; " +
                                    "var rect = elem.getBoundingClientRect(); " +
                                    "return (rect.width > 0 && rect.height > 0) && " +
                                    "window.getComputedStyle(elem).visibility === 'visible' && " +
                                    "window.getComputedStyle(elem).display !== 'none';",
                            element);
                    if (Boolean.FALSE.equals(visible)) return null;
                } catch (Exception e) {
                    logger.debug("JS visibility check failed for {}: {}", locator, e.getMessage());
                }

                if (!element.isDisplayed() || !element.isEnabled()) return null;
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
            return StringUtils.isNotBlank(actualText) &&
                    actualText.trim().matches(expectedText.substring(6).trim());
        }
        if (expectedText.startsWith("equals:")) {
            return StringUtils.isNotBlank(actualText) &&
                    actualText.trim().equals(expectedText.substring(7).trim());
        }
        if (expectedText.startsWith("icontains:")) {
            return StringUtils.isNotBlank(actualText) &&
                    actualText.toLowerCase().contains(expectedText.substring(10).trim().toLowerCase());
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
