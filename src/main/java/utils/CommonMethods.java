package utils;

import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Random;
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


    public CommonMethods(WebDriver driver, TestData testData) {
        this.driver = driver;
        this.testData = testData;
        this.retryHandler = new RetryHandler(driver, testData);
        this.scrollUtils = new ScrollUtils(driver);

    }

    /*    Wait methods */

    public void waitForPresenceOfElement(By locator) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
        wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    public void waitForStalenessOfElement(WebElement element) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
        wait.until(ExpectedConditions.stalenessOf(element));
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

    public void waitForElementToDisappear(By locator) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }


    /* Action methods */

    public void inputText(By locator, String text) {
        inputText(locator, text, null);
    }

    public void inputText(By locator, String text, Keys key) {
        retryHandler.retryAction(locator, element -> {
            element.clear();
            element.sendKeys(text);
            if (key != null) element.sendKeys(key);
            logger.info("Input text '{}' into element located by {}", text, locator);
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
            logger.info("Clicked element located by {}", locator);
        }, ActionType.CLICK, RetryOptions.none());
    }

    public void clickByJS(By locator) {
        retryHandler.retryAction(locator, element -> {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            logger.info("Clicked (via JS) element located by {}", locator);
        }, ActionType.JS_CLICK, RetryOptions.none());
    }


    public void triggerLazyLoadingScroll() {
        scrollUtils.scrollThroughPageToTriggerLazyLoading();
    }

    public void waitForPageToLoad(String pageName) {
        try {
            logger.debug("Waiting for page to load: {}", pageName);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getPageLoadTimeout()));
            wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

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
                Object result = ((JavascriptExecutor) d)
                        .executeScript("return window.jQuery ? jQuery.active : 0");
                return result instanceof Long && (Long) result == 0;
            });
        } catch (Exception e) {
            logger.debug("jQuery not present or wait not needed: {}", e.getMessage());
        }
    }

    public void standardSelect(By dropdownLocator, String visibleText) {
        retryHandler.retryAction(dropdownLocator, element -> {
            Select select = new Select(element);
            select.selectByVisibleText(visibleText);
            logger.info("Selected '{}' from dropdown located by {}", visibleText, dropdownLocator);
        }, ActionType.CLICK, RetryOptions.none());
    }

    private List<WebElement> findElements(By locator) {
        return driver.findElements(locator);
    }

    public void clickRandomWebElement(By locator) {
        retryHandler.retryAction(locator, element -> {
            List<WebElement> elements = driver.findElements(locator);

            if (elements.isEmpty()) {
                throw new NoSuchElementException("No elements found for locator: " + locator);
            }

            WebElement randomElement = elements.get(new Random().nextInt(elements.size()));
            logger.info("Clicking random element from locator: {}", locator);

            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", randomElement);

                try {
                    randomElement.click();
                } catch (ElementClickInterceptedException ex) {
                    logger.warn("Click intercepted, using JS click as fallback");
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", randomElement);
                }

            } catch (StaleElementReferenceException staleEx) {
                logger.warn("Stale element encountered, retrying...");
                throw staleEx;
            }
        }, ActionType.CLICK, RetryOptions.none());
    }

    public void selectADFDropdownOption(By dropdownLocator, String optionText) {
        try {
            waitForElementToBeVisibleAndClickable(dropdownLocator);
            clickByJS(dropdownLocator);

            By optionsLocator = By.xpath("//td[normalize-space(text())='" + optionText + "']");
            waitForElementToBeClickable(optionsLocator);

            retryHandler.retryAction(optionsLocator, element -> {
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
                clickByJS(optionsLocator);
            }, ActionType.JS_CLICK, RetryOptions.none());

            retryHandler.retryAction(dropdownLocator, element -> {
                element.sendKeys(Keys.TAB);
                logger.info("Sent TAB to confirm selection for dropdown {}", dropdownLocator);
            }, ActionType.INPUT, RetryOptions.none());

            logger.info("Selected option '{}' from ADF dropdown {}", optionText, dropdownLocator);

        } catch (Exception e) {
            String errorMsg = "Failed to select option '" + optionText + "' from ADF dropdown: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }


    public void waitForElementTextToNonEmpty(By locator) {
        retryHandler.retryAction(locator,
                element -> logger.info("Text is now populated: '{}'", element.getText()),
                RetryHandler.ActionType.TEXT_NON_EMPTY,
                RetryHandler.RetryOptions.none());
    }

    public void waitForElementTextToMatch(By locator, String expectedText) {
        retryHandler.retryAction(locator, element ->
                        logger.info("Checking for expected: '{}'", expectedText),
                ActionType.TEXT_MATCH, RetryOptions.expectedText(expectedText));
    }

    public void waitForElementAttributeToNonEmpty(By locator, String attributeName) {
        retryHandler.retryAction(locator, element ->
                        logger.info("Attribute updated '{}'", attributeName),
                ActionType.ATTRIBUTE_NON_EMPTY, RetryOptions.attribute(attributeName));
    }

    public void waitForElementAttributeToMatch(By locator, String attributeName, String expectedValue) {
        retryHandler.retryAction(locator, element ->
                        logger.info("Checking attribute '{}' for expected: '{}'", attributeName, expectedValue),
                ActionType.ATTRIBUTE_MATCH, RetryOptions.attributeMatch(expectedValue, attributeName));
    }

    public String getElementAttribute(By locator) {
        return getElementAttribute(locator, VALUE);
    }

    public String getElementAttribute(By locator, String attribute) {
        final String attr = (attribute == null || attribute.isBlank()) ? VALUE : attribute;

        try {
            AtomicReference<String> result = new AtomicReference<>(EMPTY_STRING);
            retryHandler.retryAction(locator, element -> {
                String value = element.getAttribute(attr);
                result.set(value != null ? value : EMPTY_STRING);
                logger.info("Retrieved attribute '{}' = '{}' from element {}", attr, result.get(), locator);
            }, ActionType.READ, RetryOptions.none());

            return result.get();
        } catch (RetryHandler.ElementActionFailedException e) {
            logger.warn("Failed to retrieve attribute '{}' from element {}: {}", attr, locator, e.getMessage());
            return EMPTY_STRING;
        }
    }

    public String getElementText(By locator) {
        AtomicReference<String> text = new AtomicReference<>(EMPTY_STRING);
        retryHandler.retryAction(locator, element -> {
            String value = element.getText();
            text.set(value.trim());
            logger.info("Retrieved text '{}' from element {}", text.get(), locator);
        }, ActionType.READ, RetryOptions.none());
        return text.get();
    }

    public void inputKey(By locator, Keys key) {
        waitForElementToBeVisibleAndClickable(locator);
        retryHandler.retryAction(locator, element -> {
            element.sendKeys(key);
            logger.info("Sent {} key to element located by {}", key, locator);
        }, ActionType.INPUT, RetryOptions.none());
    }

    public void uploadFile(By fileInputLocator, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("File path must not be null or empty");
        }
        retryHandler.retryAction(fileInputLocator, element -> {
            element.sendKeys(filePath);
            logger.info("Uploaded file '{}' using input located by {}", filePath, fileInputLocator);
        }, ActionType.INPUT, RetryOptions.none());
    }

    public void waitForElementReplacement(By oldLocator, By newLocator) {
        retryHandler.retryAction(newLocator, element -> {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));

            try {
                WebElement oldElement = driver.findElement(oldLocator);
                wait.until(ExpectedConditions.stalenessOf(oldElement));
                logger.info("Old element {} became stale", oldLocator);
            } catch (NoSuchElementException e) {
                logger.debug("Old element {} not present, skipping staleness check", oldLocator);
            }

            wait.until(ExpectedConditions.visibilityOf(element));
            logger.info("New element {} is visible after replacement", newLocator);

        }, ActionType.READ, RetryOptions.none());
    }

    public void waitForOverlayToDisappear(By overlayLocator) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(3));
            shortWait.until(ExpectedConditions.presenceOfElementLocated(overlayLocator));
            logger.debug("Overlay appeared, now waiting for it to disappear...");
        } catch (TimeoutException e) {
            logger.debug("Overlay not present, proceeding without wait");
            return;
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
            retryHandler.retryAction(locator, element -> {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(testData.getExplicitWait()));
                wait.until(d -> {
                    String text = element.getText().trim();
                    return !text.isEmpty();
                });
                logger.info("Span located by {} is now populated", locator);
            }, ActionType.READ, RetryOptions.none());
        } catch (RetryHandler.ElementActionFailedException e) {
            String errorMsg = String.format("Span %s was not populated within %d seconds",
                    locator, testData.getExplicitWait());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public String getCurrentMonthYear() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM-yy", Locale.US);
        return LocalDate.now().format(formatter);
    }

}