package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.PageFactory;
import utils.CommonMethods;
import utils.TestData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoginPage extends CommonMethods {

    private static final Logger logger = LogManager.getLogger(LoginPage.class);

    private static final By usernameTxtBox = By.xpath("//input[@id='user-name']");
    private static final By passwordTxtBox = By.xpath("//input[@id='password']");
    private static final By loginBtn = By.xpath("//input[@id='login-button']");
    private static final By images = By.xpath("//*[@id='tabletsImg']/div");



    public LoginPage(WebDriver driver, TestData testData) {
        super(driver, testData);
        PageFactory.initElements(driver, this);
    }

    private void enterUserId(String username) {
        inputText(usernameTxtBox, username);
    }

    private void enterPassword(String password) {
        inputText(passwordTxtBox, password);
    }

    private void clickLoginButton() {
        clickElement(loginBtn);
    }

    public void login(String username, String password) {
        logger.info("Logging in with username: {}", username);
        enterUserId(username);
        enterPassword(password);
        clickLoginButton();
    }

    public boolean isPageLoaded() {
        waitForPageToLoad("Login Page");  // Wait for DOM ready
        waitForVisibilityOfElement(images);      // Wait for a key element
        return true;
    }

}
