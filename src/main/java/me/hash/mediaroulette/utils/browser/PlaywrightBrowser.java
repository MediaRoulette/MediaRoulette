package me.hash.mediaroulette.utils.browser;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Utility class for Playwright browser automation with stealth features
 */
public class PlaywrightBrowser {
    private static final Logger logger = LoggerFactory.getLogger(PlaywrightBrowser.class);

    /**
     * Execute a function with a stealth browser page
     */
    public static <T> T executeWithPage(String url, Function<Page, T> pageFunction) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true) // Use headless for stability
                .setArgs(java.util.Arrays.asList(
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-blink-features=AutomationControlled",
                    "--disable-web-security",
                    "--disable-features=VizDisplayCompositor",
                    "--disable-dev-shm-usage",
                    "--no-sandbox"
                )));
            
            try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
                .setLocale("en-US")
                .setTimezoneId("America/New_York")
                .setExtraHTTPHeaders(java.util.Map.of(
                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                    "Accept-Language", "en-US,en;q=0.5",
                    "Accept-Encoding", "gzip, deflate, br",
                    "DNT", "1",
                    "Connection", "keep-alive",
                    "Upgrade-Insecure-Requests", "1"
                )))) {

                try (Page page = context.newPage()) {
                    // Remove webdriver detection
                    page.addInitScript("() => {" +
                            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                            "window.chrome = {runtime: {}};" +
                            "Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});" +
                            "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});" +
                            "}");

                    // Navigate with timeout and proper error handling
                    page.navigate(url, new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(15000));

                    // Wait for page to be ready
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                            new Page.WaitForLoadStateOptions().setTimeout(10000));

                    return pageFunction.apply(page);

                } catch (Exception ignored) {
                }
            }
            
        } catch (Exception e) {
            logger.error("Browser execution failed for URL: {} - {}", url, e.getMessage());
            throw new RuntimeException("Browser execution failed: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Execute a function with a visible browser for debugging
     */
    public static <T> T executeWithVisiblePage(String url, Function<Page, T> pageFunction) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(java.util.Arrays.asList(
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-blink-features=AutomationControlled",
                    "--disable-web-security",
                    "--disable-features=VizDisplayCompositor"
                )));
            
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
                .setLocale("en-US")
                .setTimezoneId("America/New_York")
                .setExtraHTTPHeaders(java.util.Map.of(
                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                    "Accept-Language", "en-US,en;q=0.5",
                    "Accept-Encoding", "gzip, deflate, br",
                    "DNT", "1",
                    "Connection", "keep-alive",
                    "Upgrade-Insecure-Requests", "1"
                )));
            
            Page page = context.newPage();
            
            // Remove webdriver detection
            page.addInitScript("() => {" +
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                "window.chrome = {runtime: {}};" +
                "Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});" +
                "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});" +
                "}");
            
            page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.NETWORKIDLE));
            
            page.waitForTimeout(3000); // Brief wait for content to load

            return pageFunction.apply(page);
            
        } catch (Exception e) {
            throw new RuntimeException("Browser execution failed: " + e.getMessage(), e);
        }
    }
}