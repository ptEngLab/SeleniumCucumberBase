package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.PageFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.CommonMethods;
import utils.TestData;

public class HomePage extends CommonMethods {

    private static final Logger logger = LogManager.getLogger(HomePage.class);

    private static final By usernameTxtBox = By.cssSelector("input#userid");
    private static final By passwordTxtBox = By.cssSelector("input#password");
    private static final By loginBtn = By.xpath("//button[contains(text(),'Sign In')]");

    public HomePage(WebDriver driver, TestData testData) {
        super(driver, testData);
        PageFactory.initElements(driver, this);
    }
}
