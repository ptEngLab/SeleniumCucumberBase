package utils;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v138.page.Page;
import org.openqa.selenium.edge.EdgeDriver;

import java.util.Base64;
import java.util.Optional;

public class ScreenshotUtils {

    public static byte[] captureFullPageScreenshotWithCDP(EdgeDriver driver) {
        DevTools devTools = driver.getDevTools();
        devTools.createSession();
        devTools.send(Page.enable(Optional.empty()));

        String base64Screenshot = devTools.send(Page.captureScreenshot(
                Optional.of(Page.CaptureScreenshotFormat.PNG),      // format
                Optional.empty(),                                   // Quality (not used for PNG)
                Optional.empty(),                                   // clip (Viewport)
                Optional.of(true),                            // fromSurface
                Optional.of(true),                            // captureBeyondViewport
                Optional.of(false)                            // optimizeForSpeed

        ));

        return Base64.getDecoder().decode(base64Screenshot);

    }

    public static byte[] captureStandardScreenshot(WebDriver driver) {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }
}
