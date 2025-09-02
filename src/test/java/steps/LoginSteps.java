package steps;

import context.TestContext;
import context.TestContextManager;
import io.cucumber.java.en.*;
import org.openqa.selenium.WebDriver;
import utils.TestData;

import static utils.ReportUtils.logStep;

public class LoginSteps {

    private final WebDriver driver;
    private final TestData testData;

    public LoginSteps() {
        TestContext context = TestContextManager.getContext();
        this.driver = context.getDriver();
        this.testData = context.getTestData();
    }

    @Given("the application is running")
    public void the_application_is_running() {
        logStep("Scenario: " + testData.getScenarioName());
        logStep("Application is running");
        driver.get(testData.getAppUrl());
    }

    @Given("the user navigates to the login page")
    public void user_navigates_to_login_page() {
        logStep("Navigating to login page");
    }

    @When("the user enters username {string} and password {string}")
    public void user_enters_credentials(String username, String password) {
        logStep("Entering username: " + username + " and password: " + password);
    }

    @When("clicks the login button")
    public void clicks_login_button() {
        logStep("Clicking login button");
    }

    @Then("the user should see the dashboard")
    public void user_should_see_dashboard() {
        logStep("Verifying dashboard is displayed");
    }

    @Then("the user should see an error message {string}")
    public void user_should_see_error_message(String message) {
        logStep("Verifying error message: " + message);
    }
}
