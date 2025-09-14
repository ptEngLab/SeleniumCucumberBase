package utils;

import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static utils.Constants.*;
import static utils.RetryHandler.ActionType;
import static utils.RetryHandler.RetryOptions;

@RequiredArgsConstructor
public class CommonMethods {

    private static final Logger logger = LogManager.getLogger(CommonMethods.class);

    protected final WebDriver driver;
    protected final TestData testData;
    protected final RetryHandler retryHandler;
    private final ScrollUtils scrollUtils;

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER =
            DateTimeFormatter.ofPattern("MMM-yy", Locale.US);

    public CommonMethods(WebDriver driver, TestData testData) {
        this.driver = driver;
        this.testData = testData;
        this.retryHandler = new RetryHandler(driver, testData);
        this.scrollUtils = new ScrollUtils(driver);
    }

    /* -----------------------------------------
       Utility Wait Helpers
    ----------------------------------------- */
    private WebDriverWait getWait() {
        return new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
    }

    /* -----------------------------------------
       Wait Methods
    ----------------------------------------- */
    public void waitForPresenceOfElement(By locator) {
        getWait().until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    public void waitForStalenessOfElement(WebElement element) {
        getWait().until(ExpectedConditions.stalenessOf(element));
    }

    public void waitForVisibilityOfElement(By locator) {
        getWait().until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public void waitForElementToBeVisibleAndClickable(By locator) {
        getWait().until(ExpectedConditions.visibilityOfElementLocated(locator));
        getWait().until(ExpectedConditions.elementToBeClickable(locator));
    }

    public void waitForElementToBeClickable(By locator) {
        getWait().until(ExpectedConditions.elementToBeClickable(locator));
    }

    public void waitForElementToDisappear(By locator) {
        getWait().until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    public void waitForValue(By locator, String expectedValue) {
        getWait().until(ExpectedConditions.attributeToBe(locator, "value", expectedValue));
    }

    /* -----------------------------------------
       Core Action Methods
    ----------------------------------------- */
    public void inputText(By locator, String text) {
        inputText(locator, text, null);
    }

    public void inputText(By locator, String text, Keys key) {
        retryHandler.retryAction(locator, element -> {
            element.clear();
            element.sendKeys(text);
            if (key != null) element.sendKeys(key);
            logger.info("Input text '{}' into element {}", text, locator);
        }, ActionType.INPUT, RetryOptions.none());
    }

    public void inputTextAndSubmit(By locator, String text) {
        WebElement oldBody = driver.findElement(By.tagName("body"));
        inputText(locator, text, Keys.ENTER);
        waitForStalenessOfElement(oldBody);
        waitForPageToLoad("Page after refresh");
    }

    public void clickElement(By locator) {
        retryHandler.retryAction(locator, element -> {
            element.click();
            logger.info("Clicked element {}", locator);
        }, ActionType.CLICK, RetryOptions.none());
    }

    public void clickElementAndWait(By locator, By confirmationLocator) {
        clickElement(locator);
        getWait().until(ExpectedConditions.visibilityOfElementLocated(confirmationLocator));
        logger.info("Confirmed element {} appeared after clicking {}", confirmationLocator, locator);
    }

    public void clickByJS(By locator) {
        retryHandler.retryAction(locator, element -> {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            logger.info("Clicked (via JS) element {}", locator);
        }, ActionType.JS_CLICK, RetryOptions.none());
    }

    public void inputKey(By locator, Keys key) {
        waitForElementToBeVisibleAndClickable(locator);
        retryHandler.retryAction(locator, element -> {
            element.sendKeys(key);
            logger.info("Sent {} key to element {}", key, locator);
        }, ActionType.INPUT, RetryOptions.none());
    }

    /* -----------------------------------------
       Page Load Handling
    ----------------------------------------- */
    public void triggerLazyLoadingScroll() {
        scrollUtils.scrollThroughPageToTriggerLazyLoading();
    }

    public void waitForPageToLoad(String pageName) {
        try {
            logger.debug("Waiting for page to load: {}", pageName);

            new WebDriverWait(driver, Duration.ofSeconds(testData.getPageLoadTimeout()))
                    .until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

            waitForJQueryInactive();

            logger.info("Page loaded successfully: {}", pageName);
        } catch (TimeoutException e) {
            String errorMsg = String.format("Page '%s' failed to load within %d seconds",
                    pageName, testData.getPageLoadTimeout());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    private void waitForJQueryInactive() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(d -> {
                        Object result = ((JavascriptExecutor) d)
                                .executeScript("return window.jQuery ? jQuery.active : 0");
                        return result instanceof Long && (Long) result == 0;
                    });
        } catch (Exception e) {
            logger.debug("jQuery not present or already inactive: {}", e.getMessage());
        }
    }

    /* -----------------------------------------
       Dropdowns & Selects
    ----------------------------------------- */
    public void standardSelect(By dropdownLocator, String visibleText) {
        retryHandler.retryAction(dropdownLocator, element -> {
            new Select(element).selectByVisibleText(visibleText);
            logger.info("Selected '{}' from dropdown {}", visibleText, dropdownLocator);
        }, ActionType.CLICK, RetryOptions.none());
    }

    public void selectADFDropdownOption(By dropdownLocator, String optionText) {
        try {
            waitForElementToBeVisibleAndClickable(dropdownLocator);
            clickByJS(dropdownLocator);

            By optionsLocator = By.xpath(String.format("//td[normalize-space(text())='%s']", optionText));
            waitForElementToBeClickable(optionsLocator);

            clickByJS(optionsLocator);

            retryHandler.retryAction(dropdownLocator, element -> {
                element.sendKeys(Keys.TAB);
                logger.info("Confirmed selection with TAB for dropdown {}", dropdownLocator);
            }, ActionType.INPUT, RetryOptions.none());

            logger.info("Selected option '{}' from ADF dropdown {}", optionText, dropdownLocator);
        } catch (Exception e) {
            String errorMsg = "Failed to select option '" + optionText + "' from ADF dropdown";
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /* -----------------------------------------
       Random Element Interaction
    ----------------------------------------- */
    public void clickRandomWebElement(By locator) {
        retryHandler.retryAction(locator, element -> {
            List<WebElement> elements = driver.findElements(locator);
            List<WebElement> visibleElements = elements.stream()
                    .filter(e -> e.isDisplayed() && e.isEnabled())
                    .toList();

            if (visibleElements.isEmpty()) {
                throw new NoSuchElementException("No clickable elements found for locator: " + locator);
            }

            WebElement randomElement =
                    visibleElements.get(ThreadLocalRandom.current().nextInt(visibleElements.size()));

            logger.info("Clicking random element from locator: {}", locator);

            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", randomElement);
                randomElement.click();
            } catch (ElementClickInterceptedException ex) {
                logger.warn("Click intercepted, retrying with JS click");
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", randomElement);
            }
        }, ActionType.CLICK, RetryOptions.none());
    }

    /* -----------------------------------------
       Attribute & Text Checks
    ----------------------------------------- */
    public void waitForElementTextToNonEmpty(By locator) {
        retryHandler.retryAction(locator,
                element -> logger.info("Text populated: '{}'", element.getText()),
                ActionType.TEXT_NON_EMPTY, RetryOptions.none());
    }

    public void waitForElementTextToMatch(By locator, String expectedText) {
        retryHandler.retryAction(locator,
                element -> logger.info("Text matched expected '{}'", expectedText),
                ActionType.TEXT_MATCH, RetryOptions.expectedText(expectedText));
    }

    public void waitForElementAttributeToNonEmpty(By locator, String attributeName) {
        retryHandler.retryAction(locator,
                element -> logger.info("Attribute '{}' updated", attributeName),
                ActionType.ATTRIBUTE_NON_EMPTY, RetryOptions.attribute(attributeName));
    }

    public void waitForElementAttributeToMatch(By locator, String attributeName, String expectedValue) {
        retryHandler.retryAction(locator,
                element -> logger.info("Attribute '{}' matched expected '{}'", attributeName, expectedValue),
                ActionType.ATTRIBUTE_MATCH, RetryOptions.attributeMatch(expectedValue, attributeName));
    }

    /* -----------------------------------------
       Element Retrieval
    ----------------------------------------- */
    public WebElement findElementWithRetry(By locator) {
        AtomicReference<WebElement> ref = new AtomicReference<>();
        retryHandler.retryAction(locator, ref::set, ActionType.READ, RetryOptions.none());
        return ref.get();
    }

    public String getElementAttribute(By locator) {
        return getElementAttribute(locator, VALUE);
    }

    public String getElementAttribute(By locator, String attribute) {
        final String attr = (attribute == null || attribute.isBlank()) ? VALUE : attribute;
        try {
            AtomicReference<String> result = new AtomicReference<>(EMPTY_STRING);
            retryHandler.retryAction(locator, element -> {
                result.set(element.getAttribute(attr));
                logger.debug("Retrieved attribute '{}' = '{}' from {}", attr, result.get(), locator);
            }, ActionType.READ, RetryOptions.none());
            return result.get() != null ? result.get() : EMPTY_STRING;
        } catch (RetryHandler.ElementActionFailedException e) {
            logger.warn("Failed to get attribute '{}' from {}: {}", attr, locator, e.getMessage());
            return EMPTY_STRING;
        }
    }

    public String getElementText(By locator) {
        AtomicReference<String> text = new AtomicReference<>(EMPTY_STRING);
        retryHandler.retryAction(locator, element -> {
            text.set(element.getText().trim());
            logger.debug("Retrieved text '{}' from {}", text.get(), locator);
        }, ActionType.READ, RetryOptions.none());
        return text.get();
    }

    /* -----------------------------------------
       File Upload
    ----------------------------------------- */
    public void uploadFile(By fileInputLocator, String filename) {
        File file = new File(System.getProperty("user.dir") + File.separator + filename);
        if (!file.exists()) {
            throw new RuntimeException("File not found: " + file.getAbsolutePath());
        }

        retryHandler.retryAction(fileInputLocator, element -> {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].style.display='block'; arguments[0].style.visibility='visible';", element);
            element.sendKeys(file.getAbsolutePath());
            logger.info("Uploaded file '{}' to {}", file.getAbsolutePath(), fileInputLocator);
        }, ActionType.INPUT, RetryOptions.none());
    }

    /* -----------------------------------------
       Complex Waits
    ----------------------------------------- */
    public void waitForElementReplacement(By oldLocator, By newLocator) {
        retryHandler.retryAction(newLocator, element -> {
            try {
                WebElement oldElement = driver.findElement(oldLocator);
                getWait().until(ExpectedConditions.stalenessOf(oldElement));
                logger.info("Old element {} became stale", oldLocator);
            } catch (NoSuchElementException e) {
                logger.debug("Old element {} not found, skipping staleness check", oldLocator);
            }
            getWait().until(ExpectedConditions.visibilityOf(element));
            logger.info("New element {} is now visible", newLocator);
        }, ActionType.READ, RetryOptions.none());
    }

    public void waitForOverlayToDisappear(By overlayLocator) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(ExpectedConditions.presenceOfElementLocated(overlayLocator));
            logger.debug("Overlay appeared, waiting to disappear...");
        } catch (TimeoutException ignored) {
            logger.debug("Overlay {} not present, skipping", overlayLocator);
            return;
        }
        getWait().until(ExpectedConditions.invisibilityOfElementLocated(overlayLocator));
        logger.info("Overlay {} disappeared", overlayLocator);
    }

    public void waitForSpanToBePopulated(By locator) {
        retryHandler.retryAction(locator, element -> {
            getWait().until(d -> !element.getText().trim().isEmpty());
            logger.info("Span {} is populated", locator);
        }, ActionType.READ, RetryOptions.none());
    }

    /* -----------------------------------------
       Miscellaneous
    ----------------------------------------- */
    public String getCurrentMonthYear() {
        return LocalDate.now().format(MONTH_YEAR_FORMATTER);
    }
}
