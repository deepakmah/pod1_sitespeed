package Speed;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

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

    private static String resolveApiKey() {
        String env = System.getenv("IMGBB_API_KEY");
        if (env != null && !env.trim().isEmpty()) {
            return env;
        }
        return "46866c7eef7ee62b26a79f32a5d57a08"; // fallback for local runs only
    }

    private static final String API_KEY = resolveApiKey();

    private static final String CSV_PATH = System.getenv("PAGESPEED_CSV_PATH") != null
            ? System.getenv("PAGESPEED_CSV_PATH")
            : (System.getProperty("user.home") + (System.getProperty("os.name").toLowerCase().contains("win") ? "\\Documents\\pagespeed_results.csv" : "/pagespeed_results.csv"));

    // Core Web Vital metric ids as they appear in the Lighthouse report DOM
    private static final String[] METRIC_IDS = {
            "first-contentful-paint",
            "largest-contentful-paint",
            "total-blocking-time",
            "cumulative-layout-shift",
            "speed-index"
    };

    public static void main(String[] args) {

        String[] websites = {
                "https://www.colbrookkitchen.com"
               
        };

        createCsvHeader();

        for (String site : websites) {
            runForSite(site);
        }

        System.out.println("\n=== ALL DONE SUCCESSFULLY ===");
    }

    private static void createCsvHeader() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_PATH, false))) {
            writer.println(
                    "Date,Website," +
                    "Desktop Score,Desktop FCP,Desktop LCP,Desktop TBT,Desktop CLS,Desktop Speed Index,Desktop Screenshot URL," +
                    "Mobile Score,Mobile FCP,Mobile LCP,Mobile TBT,Mobile CLS,Mobile Speed Index,Mobile Screenshot URL"
            );
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void appendToCsv(String date, String site,
                                     String desktopScore, String[] desktopMetrics, String desktopURL,
                                     String mobileScore, String[] mobileMetrics, String mobileURL) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_PATH, true))) {
            StringBuilder row = new StringBuilder();
            row.append(date).append(",").append(site).append(",")
               .append(desktopScore).append(",")
               .append(String.join(",", desktopMetrics)).append(",")
               .append(desktopURL).append(",")
               .append(mobileScore).append(",")
               .append(String.join(",", mobileMetrics)).append(",")
               .append(mobileURL);
            writer.println(row);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void runForSite(String site) {

        WebDriver driver = null;
        WebDriverWait wait;

        String desktopURL = "FAILED";
        String mobileURL = "FAILED";
        String desktopScore = "N/A";
        String mobileScore = "N/A";
        String[] desktopMetrics = emptyMetrics();
        String[] mobileMetrics = emptyMetrics();

        try {
            System.out.println("\nRunning PageSpeed for: " + site);

            ChromeOptions options = new ChromeOptions();
            boolean headless = "true".equalsIgnoreCase(System.getenv("CI")) || "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
            if (headless) {
                options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--window-size=1920,1080");
            } else {
                options.addArguments("--start-maximized");
            }

            driver = new ChromeDriver(options);
            wait = new WebDriverWait(driver, Duration.ofSeconds(90));

            driver.get("https://pagespeed.web.dev/");
            Thread.sleep(2000);

            WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//input[@id='i2']")));
            input.clear();
            input.sendKeys(site);
            Thread.sleep(1000);

            WebElement analyzeBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[normalize-space()='Analyze']")));
            analyzeBtn.click();

            Thread.sleep(2000);

            // DESKTOP
            selectTab(wait, "desktop_tab");
            desktopScore = waitForScore(driver, "desktop_tab");
            desktopMetrics = extractCoreWebVitals(driver);
            scrollToReport(driver, wait);
            ensureTabActive(driver, wait, "desktop_tab");
            desktopURL = takeSSAndUpload(driver, sanitize(site) + "_desktop");

            // MOBILE
            selectTab(wait, "mobile_tab");
            mobileScore = waitForScore(driver, "mobile_tab");
            mobileMetrics = extractCoreWebVitals(driver);
            scrollToReport(driver, wait);
            ensureTabActive(driver, wait, "mobile_tab");
            mobileURL = takeSSAndUpload(driver, sanitize(site) + "_mobile");

            System.out.println("✔ Completed for: " + site);

        } catch (Exception e) {
            System.out.println("⚠ FAILED: " + site + " | " + e.getMessage());
        }
        finally {
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

            appendToCsv(today, site, desktopScore, desktopMetrics, desktopURL, mobileScore, mobileMetrics, mobileURL);

            if (driver != null) driver.quit();
        }
    }

    private static String[] emptyMetrics() {
        return new String[]{"N/A", "N/A", "N/A", "N/A", "N/A"};
    }

    /**
     * Reads the 5 Core Web Vital values (FCP, LCP, TBT, CLS, Speed Index) from the
     * currently active Lighthouse report tab. Returns "N/A" for any metric not found,
     * so a DOM/markup change on one metric never blows up the whole row.
     */
    private static String[] extractCoreWebVitals(WebDriver driver) {
        String[] values = new String[METRIC_IDS.length];
        for (int i = 0; i < METRIC_IDS.length; i++) {
            values[i] = extractMetric(driver, METRIC_IDS[i]);
        }
        return values;
    }

    private static String extractMetric(WebDriver driver, String metricId) {
        try {
            WebElement el = driver.findElement(By.cssSelector("#" + metricId + " .lh-metric__value"));
            String txt = el.getText().trim();
            // normalize the non-breaking space Lighthouse uses between number and unit (e.g. "2.6 s")
            return txt.replace('\u00A0', ' ');
        } catch (Exception e) {
            return "N/A";
        }
    }

    private static void selectTab(WebDriverWait wait, String id) throws Exception {
        WebElement tab = wait.until(
                ExpectedConditions.elementToBeClickable(By.id(id))
        );
        tab.click();
        Thread.sleep(3000);
    }

    private static void ensureTabActive(WebDriver driver, WebDriverWait wait, String tabId) throws Exception {
        WebElement tab = wait.until(ExpectedConditions.elementToBeClickable(By.id(tabId)));
        if (!"true".equals(tab.getAttribute("aria-selected"))) {
            tab.click();
            Thread.sleep(2000);
        }
    }

    private static String waitForScore(WebDriver driver, String tabId) {
        long end = System.currentTimeMillis() + 60000;

        while (System.currentTimeMillis() < end) {
            try {
                WebElement tab = driver.findElement(By.id(tabId));
                if (!"true".equals(tab.getAttribute("aria-selected"))) {
                    tab.click();
                    Thread.sleep(2000);
                    continue;
                }

                java.util.List<WebElement> scores =
                        driver.findElements(By.cssSelector(".lh-exp-gauge__percentage"));

                for (WebElement s : scores) {
                    if (s.isDisplayed()) {
                        String txt = s.getText().trim();
                        if (!txt.isEmpty() && txt.matches("\\d+")) {
                            return txt;
                        }
                    }
                }
            } catch (Exception ignore) {}
            try { Thread.sleep(700); } catch (Exception ignore) {}
        }
        return "N/A";
    }

    private static void scrollToReport(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement ele = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("(//div[normalize-space()='Diagnose performance issues'])[last()]")));
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({behavior:'auto',block:'center'})", ele);
            Thread.sleep(1500);
        } catch (Exception ignore) {}
    }

    private static String takeSSAndUpload(WebDriver driver, String filename) {
        try {
            File scr = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            byte[] data = Files.readAllBytes(scr.toPath());
            return upload(data, filename);
        } catch (Exception e) {
            return "FAILED";
        }
    }

    private static String upload(byte[] img, String filename) {
        try {
            String base64 = Base64.getEncoder().encodeToString(img);
            String url = "https://api.imgbb.com/1/upload?key=" + API_KEY;

            HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");

            String data = "image=" + URLEncoder.encode(base64,"UTF-8")
                    + "&name=" + URLEncoder.encode(filename,"UTF-8");

            conn.getOutputStream().write(data.getBytes());

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String res = br.readLine();
            br.close();

            int start = res.indexOf("\"url\":\"") + 7;
            int end = res.indexOf("\"", start);
            return res.substring(start, end);

        } catch (Exception e) {
            System.out.println("Upload failed: " + e.getMessage());
            return "FAILED";
        }
    }

    private static String sanitize(String url) {
        return url.replace("https://","")
                .replace("http://","")
                .replace("www.","")
                .replaceAll("[^a-zA-Z0-9]","_");
    }
}
