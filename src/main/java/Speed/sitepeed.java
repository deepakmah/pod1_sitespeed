package Speed;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

public class sitepeed {

    private static final String API_KEY = "46866c7eef7ee62b26a79f32a5d57a08";
    private static final String CSV_PATH = System.getenv("PAGESPEED_CSV_PATH") != null
            ? System.getenv("PAGESPEED_CSV_PATH")
            : (System.getProperty("user.home") + (System.getProperty("os.name").toLowerCase().contains("win") ? "\\Documents\\pagespeed_results.csv" : "/pagespeed_results.csv"));

    private static final int MAX_RETRIES = 3;
    private static final int SCORE_WAIT_TIMEOUT = 120; // seconds

    public static void main(String[] args) {

        String[] websites = {
                "https://www.colbrookkitchen.com",
                "https://greatcellsolarmaterials.com/",
                "https://allfasteners.com/",
                "https://www.shopdap.com/",
                "https://www.mcfeelys.com/",
                "https://www.natlallergy.com/",
                "https://www.achooallergy.com/",
                "https://www.bandagesplus.com/",
                "https://oldchevytrucks.com/",
                "https://nutridyn.com/"
        };

        log("=== PAGESPEED AUTOMATION STARTED ===");
        createCsvHeader();

        for (String site : websites) {
            runForSite(site);
        }

        log("=== ALL DONE SUCCESSFULLY ===");
    }

    private static void createCsvHeader() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_PATH, false))) {
            writer.println("Date,Website,Desktop Score,Mobile Score,Desktop Screenshot URL,Mobile Screenshot URL");
            log("CSV header created at: " + CSV_PATH);
        } catch (Exception e) {
            log("ERROR creating CSV header: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void appendToCsv(String date, String site, String desktopScore, String mobileScore,
                                    String desktopURL, String mobileURL) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_PATH, true))) {
            writer.println(date + "," + site + "," + desktopScore + "," + mobileScore + "," + desktopURL + "," + mobileURL);
            log("✔ CSV row added for: " + site);
        } catch (Exception e) {
            log("ERROR appending to CSV: " + e.getMessage());
        }
    }

    private static void runForSite(String site) {

        WebDriver driver = null;
        WebDriverWait wait;

        String desktopURL = "FAILED";
        String mobileURL = "FAILED";
        String desktopScore = "N/A";
        String mobileScore = "N/A";

        try {
            log("\n>>> Running PageSpeed for: " + site);

            ChromeOptions options = new ChromeOptions();
            boolean headless = "true".equalsIgnoreCase(System.getenv("CI")) || "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
            if (headless) {
                options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--window-size=1920,1080");
                log("Running in HEADLESS mode");
            } else {
                options.addArguments("--start-maximized");
                log("Running in HEADED mode");
            }

            driver = new ChromeDriver(options);
            wait = new WebDriverWait(driver, Duration.ofSeconds(90));

            log("Loading PageSpeed Insights...");
            driver.get("https://pagespeed.web.dev/");
            Thread.sleep(3000);

            // Try multiple strategies to find and fill the input field
            log("Searching for URL input field...");
            WebElement input = findInputField(wait, driver);
            if (input == null) {
                throw new Exception("Could not find URL input field - PageSpeed Insights page structure may have changed");
            }

            log("Found input field, entering URL: " + site);
            input.clear();
            input.sendKeys(site);
            Thread.sleep(1000);

            // Click Analyze button
            log("Clicking Analyze button...");
            WebElement analyzeBtn = findAnalyzeButton(wait, driver);
            if (analyzeBtn == null) {
                throw new Exception("Could not find Analyze button");
            }
            analyzeBtn.click();
            Thread.sleep(3000);

            // DESKTOP
            log("Processing DESKTOP results...");
            selectTab(wait, driver, "desktop");
            desktopScore = waitForScore(driver, wait, "desktop");
            log("Desktop score: " + desktopScore);
            
            Thread.sleep(2000);
            scrollToReport(driver, wait);
            ensureTabActive(driver, wait, "desktop");
            
            Thread.sleep(2000);
            desktopURL = takeSSAndUpload(driver, sanitize(site) + "_desktop");
            log("Desktop screenshot URL: " + desktopURL);

            // MOBILE
            log("Processing MOBILE results...");
            selectTab(wait, driver, "mobile");
            mobileScore = waitForScore(driver, wait, "mobile");
            log("Mobile score: " + mobileScore);
            
            Thread.sleep(2000);
            scrollToReport(driver, wait);
            ensureTabActive(driver, wait, "mobile");
            
            Thread.sleep(2000);
            mobileURL = takeSSAndUpload(driver, sanitize(site) + "_mobile");
            log("Mobile screenshot URL: " + mobileURL);

            log("✔ SUCCESS: " + site);

        } catch (Exception e) {
            log("⚠ FAILED: " + site + " | Error: " + e.getMessage());
            e.printStackTrace();
        }
        finally {
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            appendToCsv(today, site, desktopScore, mobileScore, desktopURL, mobileURL);

            if (driver != null) {
                try {
                    driver.quit();
                    log("Driver closed");
                } catch (Exception e) {
                    log("Error closing driver: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Try multiple strategies to find the URL input field
     */
    private static WebElement findInputField(WebDriverWait wait, WebDriver driver) {
        // Strategy 1: Original ID selector
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//input[@id='i2']")));
        } catch (Exception e) {
            log("  Strategy 1 (id='i2') failed, trying alternative...");
        }

        // Strategy 2: Input by type="url"
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//input[@type='url']")));
        } catch (Exception e) {
            log("  Strategy 2 (type='url') failed, trying alternative...");
        }

        // Strategy 3: Input by placeholder containing "url"
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//input[contains(@placeholder, 'url') or contains(@placeholder, 'URL')]")));
        } catch (Exception e) {
            log("  Strategy 3 (placeholder) failed, trying alternative...");
        }

        // Strategy 4: Any text input in the visible area
        try {
            java.util.List<WebElement> inputs = driver.findElements(By.xpath("//input[@type='text']"));
            if (!inputs.isEmpty()) {
                return inputs.get(0);
            }
        } catch (Exception e) {
            log("  Strategy 4 (input[@type='text']) failed");
        }

        return null;
    }

    /**
     * Try multiple strategies to find the Analyze button
     */
    private static WebElement findAnalyzeButton(WebDriverWait wait, WebDriver driver) {
        // Strategy 1: Original span selector
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[normalize-space()='Analyze']")));
        } catch (Exception e) {
            log("  Analyze button strategy 1 (span) failed, trying alternative...");
        }

        // Strategy 2: Button with Analyze text
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(., 'Analyze')]")));
        } catch (Exception e) {
            log("  Analyze button strategy 2 (button) failed, trying alternative...");
        }

        // Strategy 3: Any clickable element containing Analyze
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//*[contains(text(), 'Analyze')]")));
        } catch (Exception e) {
            log("  Analyze button strategy 3 (any element) failed");
        }

        return null;
    }

    private static void selectTab(WebDriverWait wait, WebDriver driver, String device) throws Exception {
        String tabId = device.equals("desktop") ? "desktop_tab" : "mobile_tab";
        
        try {
            WebElement tab = wait.until(
                    ExpectedConditions.elementToBeClickable(By.id(tabId))
            );
            tab.click();
            log("Clicked " + device + " tab");
            Thread.sleep(3000);
        } catch (Exception e) {
            log("  Tab click failed for " + device + ", retrying with JS click...");
            try {
                WebElement tab = driver.findElement(By.id(tabId));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", tab);
                Thread.sleep(3000);
                log("Clicked " + device + " tab via JavaScript");
            } catch (Exception ex) {
                throw new Exception("Could not click " + device + " tab: " + ex.getMessage());
            }
        }
    }

    private static void ensureTabActive(WebDriver driver, WebDriverWait wait, String device) throws Exception {
        String tabId = device.equals("desktop") ? "desktop_tab" : "mobile_tab";
        
        WebElement tab = wait.until(ExpectedConditions.elementToBeClickable(By.id(tabId)));
        if (!"true".equals(tab.getAttribute("aria-selected"))) {
            log("  Tab not active, clicking again...");
            tab.click();
            Thread.sleep(2000);
        }
    }

    /**
     * Wait for score to appear with improved robustness
     */
    private static String waitForScore(WebDriver driver, WebDriverWait wait, String device) {
        long end = System.currentTimeMillis() + (SCORE_WAIT_TIMEOUT * 1000);
        String tabId = device.equals("desktop") ? "desktop_tab" : "mobile_tab";
        int attempts = 0;

        while (System.currentTimeMillis() < end) {
            attempts++;
            try {
                WebElement tab = driver.findElement(By.id(tabId));
                if (!"true".equals(tab.getAttribute("aria-selected"))) {
                    tab.click();
                    Thread.sleep(2000);
                    continue;
                }

                // Strategy 1: Original CSS selector
                java.util.List<WebElement> scores =
                        driver.findElements(By.cssSelector(".lh-exp-gauge__percentage"));

                for (WebElement s : scores) {
                    try {
                        if (s.isDisplayed()) {
                            String txt = s.getText().trim();
                            if (!txt.isEmpty() && txt.matches("\\d+")) {
                                log("  Score found after " + attempts + " attempts: " + txt);
                                return txt;
                            }
                        }
                    } catch (StaleElementReferenceException ignore) {}
                }

                // Strategy 2: Look for any visible number that looks like a score (0-100)
                try {
                    java.util.List<WebElement> allElements = driver.findElements(By.xpath("//*[text() and string-length(normalize-space(text())) <= 3]"));
                    for (WebElement el : allElements) {
                        if (el.isDisplayed()) {
                            String txt = el.getText().trim();
                            if (txt.matches("^\\d{1,3}$")) {
                                int score = Integer.parseInt(txt);
                                if (score >= 0 && score <= 100) {
                                    log("  Score found (Strategy 2) after " + attempts + " attempts: " + txt);
                                    return txt;
                                }
                            }
                        }
                    }
                } catch (Exception ignore) {}

            } catch (Exception ignore) {}
            
            try { 
                Thread.sleep(1000); 
            } catch (Exception ignore) {}
        }
        
        log("  WARNING: Score not found after " + attempts + " attempts for " + device);
        return "N/A";
    }

    private static void scrollToReport(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement ele = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("(//div[normalize-space()='Diagnose performance issues'])[last()]")));
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({behavior:'auto',block:'center'})", ele);
            Thread.sleep(1500);
            log("  Scrolled to report section");
        } catch (Exception e) {
            log("  Could not scroll to report section: " + e.getMessage());
        }
    }

    private static String takeSSAndUpload(WebDriver driver, String filename) {
        try {
            log("  Taking screenshot...");
            File scr = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            log("  Screenshot taken, size: " + scr.length() + " bytes");
            
            byte[] data = Files.readAllBytes(scr.toPath());
            log("  Uploading screenshot to ImgBB...");
            
            String url = upload(data, filename);
            log("  Upload complete: " + (url.equals("FAILED") ? "FAILED" : "SUCCESS"));
            
            return url;
        } catch (Exception e) {
            log("  ERROR in screenshot: " + e.getMessage());
            return "FAILED";
        }
    }

    private static String upload(byte[] img, String filename) {
        try {
            String base64 = Base64.getEncoder().encodeToString(img);
            log("    Base64 encoded: " + base64.length() + " chars");
            
            String url = "https://api.imgbb.com/1/upload?key=" + API_KEY;

            HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            String data = "image=" + URLEncoder.encode(base64,"UTF-8")
                    + "&name=" + URLEncoder.encode(filename,"UTF-8");

            conn.getOutputStream().write(data.getBytes());

            int responseCode = conn.getResponseCode();
            log("    ImgBB response code: " + responseCode);

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();

            String res = response.toString();
            log("    Response: " + res.substring(0, Math.min(200, res.length())));

            // Use JSON parser for safer extraction
            try {
                JsonObject json = JsonParser.parseString(res).getAsJsonObject();
                if (json.has("data") && json.getAsJsonObject("data").has("url")) {
                    return json.getAsJsonObject("data").get("url").getAsString();
                }
            } catch (Exception e) {
                log("    JSON parse error: " + e.getMessage());
                // Fallback to string parsing
                int start = res.indexOf("\"url\":\"") + 7;
                int end = res.indexOf("\"", start);
                if (start > 7 && end > start) {
                    return res.substring(start, end);
                }
            }

            return "FAILED";

        } catch (Exception e) {
            log("    Upload error: " + e.getMessage());
            e.printStackTrace();
            return "FAILED";
        }
    }

    private static String sanitize(String url) {
        return url.replace("https://","")
                .replace("http://","")
                .replace("www.","")
                .replaceAll("[^a-zA-Z0-9]","_");
    }

    private static void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + timestamp + "] " + message);
    }
}
