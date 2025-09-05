package utils;

import context.TestContextManager;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static utils.Constants.*;


@RequiredArgsConstructor
public class CommonMethods {

    private static final Logger logger = LogManager.getLogger(CommonMethods.class);


    protected final WebDriver driver;
    protected final TestData testData;


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

    private enum ActionType { CLICK, INPUT, JS_CLICK, READ}

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
            case JS_CLICK -> new ExpectedCondition<>() {
                @Override
                public WebElement apply(WebDriver driver) {
                    WebElement element = ExpectedConditions.presenceOfElementLocated(locator).apply(driver);
                    if (element != null && element.isDisplayed()) {
                        return element;
                    }
                    return null;
                }

                @Override
                public String toString() {
                    return "presence and visible state of element located by: " + locator;
                }
            };
            case READ -> ExpectedConditions.presenceOfElementLocated(locator);
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

    public void standardSelect(By dropdownLocator, String visibleText) {
        retryAction(dropdownLocator, element -> {
            Select select = new Select(element);
            select.selectByVisibleText(visibleText);
            logger.info("Selected '{}' from dropdown located by {}", visibleText, dropdownLocator);
        }, ActionType.CLICK);
    }

    private List<WebElement> findElements(By locator) {
        return driver.findElements(locator);
    }

    private WebElement getRandomElement(List<WebElement> elements) {
        return elements.get(new Random().nextInt(elements.size()));
    }

    public void clickRandomWebElement(By locator) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
                wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator));

                List<WebElement> elements = findElements(locator);
                if (elements.isEmpty()) {
                    logger.warn("No elements found for locator: {} on attempt {}/{}", locator, attempt, MAX_RETRIES);
                    sleep(RETRY_DELAY_MS);
                    continue;
                }

                WebElement randomElement = getRandomElement(elements);
                logger.info("Clicking random element from locator: {} (attempt {}/{})", locator, attempt, MAX_RETRIES);

                // Scroll into view
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", randomElement);

                // Wait for a element to be clickable
                new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()))
                        .until(ExpectedConditions.elementToBeClickable(randomElement));

                // Try click, fallback to JS if intercepted
                try {
                    randomElement.click();
                } catch (ElementClickInterceptedException ex) {
                    logger.warn("Click intercepted, using JS click as fallback");
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", randomElement);
                }

                return; // success

            } catch (StaleElementReferenceException | ElementNotInteractableException | TimeoutException e) {
                lastException = e;
                logger.warn("{} on random element for locator {}, attempt {}/{}", e.getClass().getSimpleName(), locator, attempt, MAX_RETRIES);
                sleep(RETRY_DELAY_MS * attempt);
            }
        }

        throw new RuntimeException("Failed to click random element from locator: " + locator +
                " after " + MAX_RETRIES + " attempts", lastException);
    }


    public void selectADFDropdownOption(By dropdownLocator, String optionText) {
        try {
            // Step 1: Open dropdown via JS click
            waitForElementToBeVisibleAndClickable(dropdownLocator);
            clickByJS(dropdownLocator);

            // Step 2: Define options locator (flexible match for ADF dropdowns)
            By optionsLocator = By.xpath("//*[normalize-space(text())='" + optionText + "']");

            // Step 3: Wait for option to be visible + clickable
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
            WebElement optionElement = wait.until(ExpectedConditions.refreshed(
                    ExpectedConditions.elementToBeClickable(optionsLocator)
            ));

            // Step 4: Scroll into view + click by JS
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", optionElement);
            clickByJS(optionsLocator);

            // Step 5: Send TAB to confirm selection
            retryAction(dropdownLocator, element -> {
                element.sendKeys(Keys.TAB);
                logger.info("Sent TAB to confirm selection for dropdown {}", dropdownLocator);
            }, ActionType.INPUT);

            logger.info("Selected option '{}' from ADF dropdown {}", optionText, dropdownLocator);

        } catch (Exception e) {
            String errorMsg = "Failed to select option '" + optionText + "' from ADF dropdown: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public void clickByJS(By locator) {
        retryAction(locator, element -> {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            logger.info("Clicked (via JS) element located by {}", locator);
        }, ActionType.JS_CLICK);
    }


    public void waitForTitleToMatchFilenamedd(By titleLocator, String filename) {

        new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()))
                .until(driver ->
                        {String titleValue = getElementAttribute(titleLocator);
                            return titleValue != null && titleValue.contains(filename);
                        }
                );
    }

    public void waitForTitleToMatchFilename(By titleLocator, String filename) {
        try {
            retryAction(titleLocator, element -> {
                String titleValue = getElementAttribute(titleLocator); // defaults to "value"
                logger.debug("Checking attribute 'value': '{}' for expected filename '{}'", titleValue, filename);

                if (titleValue == null || !titleValue.contains(filename)) {
                    throw new RuntimeException("Attribute value does not contain expected filename: " + filename);
                }

                logger.info("Title matched with expected filename '{}'", filename);
            }, ActionType.READ);
        } catch (ElementActionFailedException e) {
            String errorMsg = String.format("Title did not match filename '%s' within %d seconds",
                    filename, testData.getExplicitWait());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }


    public String getElementAttribute(By locator) {
        return getElementAttribute(locator, VALUE);
    }

    public String getElementAttribute(By locator, String attribute) {
        final String attr = (attribute == null || attribute.isBlank()) ? VALUE : attribute;

        try {
//            final String[] result = {""}; // to capture inside lambda
            AtomicReference<String> result = new AtomicReference<>(EMPTY_STRING);
            retryAction(locator, element -> {
                String value = element.getAttribute(attr);
                result.set(value != null ? value : EMPTY_STRING);
                logger.info("Retrieved attribute '{}' = '{}' from element {}", attr, result.get(), locator);
            }, ActionType.READ);

            return result.get();
        } catch (ElementActionFailedException e) {
            logger.warn("Failed to retrieve attribute '{}' from element {}: {}", attr, locator, e.getMessage());
            return EMPTY_STRING;
        }
    }

    public void uploadFile(By fileInputLocator, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("File path must not be null or empty");
        }

        retryAction(fileInputLocator, element -> {
            element.sendKeys(filePath);
            logger.info("Uploaded file '{}' using input located by {}", filePath, fileInputLocator);
        }, ActionType.INPUT);
    }


    public void waitForElementReplacement(By oldLocator, By newLocator) {
        retryAction(newLocator, element -> {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));

            // Step 1: Capture old the element if present
            try {
                WebElement oldElement = driver.findElement(oldLocator);
                wait.until(ExpectedConditions.stalenessOf(oldElement));
                logger.info("Old element {} became stale", oldLocator);
            } catch (NoSuchElementException e) {
                logger.debug("Old element {} not present, skipping staleness check", oldLocator);
            }

            // Step 2: Ensure the new element is visible
            wait.until(ExpectedConditions.visibilityOf(element));
            logger.info("New element {} is visible after replacement", newLocator);

        }, ActionType.READ);
    }

    public void inputEnterKey(By locator) {
        waitForElementToBeVisibleAndClickable(locator); // optional extra safety
        retryAction(locator, element -> {
            element.sendKeys(Keys.ENTER);
            logger.info("Sent ENTER key to element located by {}", locator);
        }, ActionType.INPUT);
    }

    public void waitForOverlayToDisappear(By overlayLocator) {

        try{
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(3));
            shortWait.until(ExpectedConditions.presenceOfElementLocated(overlayLocator));
            logger.debug("Overlay appeared, now waiting for it to disappear...");
        } catch (TimeoutException e) {
            logger.debug("Overlay not present, proceeding without wait");
            return; // Overlay not present, no need to wait
        }
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
            wait.until(ExpectedConditions.invisibilityOfElementLocated(overlayLocator));
            logger.info("Overlay located by {} has disappeared", overlayLocator);
        } catch (TimeoutException e) {
            String errorMsg = String.format("Overlay %s did not disappear within %d seconds",
                    overlayLocator, testData.getExplicitWait());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public void waitForSpanToBePopulated(By locator) {
        try {
            retryAction(locator, element -> {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
                wait.until(d -> {
                    String text = element.getText().trim();
                    return !text.isEmpty();
                });
                logger.info("Span located by {} is now populated", locator);
            }, ActionType.READ);
        } catch (ElementActionFailedException e) {
            String errorMsg = String.format("Span %s was not populated within %d seconds",
                    locator, testData.getExplicitWait());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
}
