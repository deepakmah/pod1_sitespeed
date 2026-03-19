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

    private static final String API_KEY = "3b23b07a37fbcee41d4984d100162a10";
    private static final String CSV_PATH = System.getenv("PAGESPEED_CSV_PATH") != null
            ? System.getenv("PAGESPEED_CSV_PATH")
            : (System.getProperty("user.home") + (System.getProperty("os.name").toLowerCase().contains("win") ? "\\Documents\\pagespeed_results.csv" : "/pagespeed_results.csv"));

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

        createCsvHeader();

        for (String site : websites) {
            runForSite(site);
        }

        System.out.println("\n=== ALL DONE SUCCESSFULLY ===");
    }

    private static void createCsvHeader() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_PATH, false))) {
            writer.println("Date,Website,Desktop Score,Mobile Score,Desktop Screenshot URL,Mobile Screenshot URL");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void appendToCsv(String date, String site, String desktopScore, String mobileScore,
                                    String desktopURL, String mobileURL) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_PATH, true))) {
            writer.println(date + "," + site + "," + desktopScore + "," + mobileScore + "," + desktopURL + "," + mobileURL);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void runForSite(String site) {

        WebDriver driver = null;
        WebDriverWait wait;

        String desktopURL = "FAILED";
        String mobileURL = "FAILED";
        String desktopScore = "N/A";
        String mobileScore = "N/A";

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
            desktopScore = waitForScore(driver);
            scrollToReport(driver, wait);
            desktopURL = takeSSAndUpload(driver, sanitize(site) + "_desktop");

            // MOBILE
            selectTab(wait, "mobile_tab");
            mobileScore = waitForScore(driver);
            scrollToReport(driver, wait);
            mobileURL = takeSSAndUpload(driver, sanitize(site) + "_mobile");

            System.out.println("✔ Completed for: " + site);

        } catch (Exception e) {
            System.out.println("⚠ FAILED: " + site + " | " + e.getMessage());
        }
        finally {
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

            appendToCsv(today, site, desktopScore, mobileScore, desktopURL, mobileURL);

            if (driver != null) driver.quit();
        }
    }

    private static void selectTab(WebDriverWait wait, String id) throws Exception {
        WebElement tab = wait.until(
                ExpectedConditions.elementToBeClickable(By.id(id))
        );
        tab.click();
        Thread.sleep(3000);
    }

    private static String waitForScore(WebDriver driver) {
        long end = System.currentTimeMillis() + 60000;

        while (System.currentTimeMillis() < end) {
            try {
                java.util.List<WebElement> scores =
                        driver.findElements(By.cssSelector(".lh-exp-gauge__percentage"));

                for (WebElement s : scores) {
                    String txt = s.getText().trim();
                    if (!txt.isEmpty() && txt.matches("\\d+")) {
                        return txt;
                    }
                }

                ((JavascriptExecutor) driver)
                        .executeScript("document.querySelector('button#desktop_tab')?.click()");

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
