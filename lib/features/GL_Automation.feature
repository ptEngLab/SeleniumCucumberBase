Feature: User Login
  As a registered user
  I want to log in to the application
  So that I can access my account

  Background:
    Given the application is running

  Scenario: Successful login with valid credentials
    Given the user navigates to the login page
    When the user enters username "testuser" and password "password123"
    And clicks the login button
    Then the user should see the dashboard

  Scenario: Unsuccessful login with invalid credentials
    Given the user navigates to the login page
    When the user enters username "wronguser" and password "wrongpass"
    And clicks the login button
    Then the user should see an error message "Invalid username or password"
