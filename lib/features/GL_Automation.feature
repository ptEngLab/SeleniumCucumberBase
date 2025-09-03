Feature: User Login
  As a registered user
  I want to log in to the application
  So that I can access my account

  Background:
    Given the application is running

  Scenario: Successful login with valid credentials
    Given the user navigates to the login page
    Then the user should see the dashboard

  Scenario: Unsuccessful login with invalid credentials
    Given the user navigates to the login page
    Then the user should see an error message "Invalid username or password"
