package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.PageFactory;
import utils.CommonMethods;
import utils.TestData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoginPage extends CommonMethods {

    private static final Logger logger = LogManager.getLogger(LoginPage.class);

    private static final By usernameTxtBox = By.cssSelector("input#userid");
    private static final By passwordTxtBox = By.cssSelector("input#password");
    private static final By loginBtn = By.xpath("//button[contains(text(),'Sign In')]");

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
}
