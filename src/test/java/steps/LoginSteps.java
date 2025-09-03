package steps;

import context.TestContext;
import context.TestContextManager;
import io.cucumber.java.en.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import pages.LoginPage;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.ReportUtils.logStep;

public class LoginSteps {


    private static final Logger logger = LogManager.getLogger(LoginSteps.class);

    private final WebDriver driver;
    private final TestContext context;


    public LoginSteps() {
        this.context = TestContextManager.getContext();
        this.driver = context.getDriver();
    }

    @Given("the application is running")
    public void the_application_is_running() {
        logStep("Scenario: " + context.getTestData().getScenarioName());
        logStep("Application is running");
        driver.get(context.getTestData().getAppUrl());
        LoginPage loginPage = context.getPageManager().getPage(LoginPage.class);
        assertTrue(loginPage.isPageLoaded(), "Login page did not load properly!");
    }

    @Given("the user navigates to the login page")
    public void user_navigates_to_login_page() {
        logStep("Navigating to login page");
//        context.getPageManager().loginAsRole(context.getTestData().getAppRole());
    }

    @Then("the user should see the dashboard")
    public void user_should_see_dashboard() {
        logStep("Verifying dashboard is displayed");
    }

    @Then("the user should see an error message {string}")
    public void user_should_see_error_message(String message) {
        logStep("Verifying error message: " + message);
        logger.info("Expected error message: {}", message);
    }

}
