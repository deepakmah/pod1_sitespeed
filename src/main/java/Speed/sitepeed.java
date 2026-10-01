package Speed;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
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

    // Lab data on pagespeed.web.dev often takes longer than a minute in CI.
    private static final long SCORE_WAIT_MS = 150_000;

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
            writer.println(
                    "Date,Website," +
                    "Desktop Score,Desktop FCP,Desktop LCP,Desktop TBT,Desktop CLS,Desktop Speed Index,Desktop Screenshot URL," +
                    "Mobile Score,Mobile FCP,Mobile LCP,Mobile TBT,Mobile CLS,Mobile Speed Index,Mobile Screenshot URL"
            );
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static void appendToCsv(String date, String site,
                                     String desktopScore, String[] desktopMetrics, String desktopURL,
                                     String mobileScore, String[] mobileMetrics, String mobileURL) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_PATH, true))) {
            java.util.List<String> fields = new java.util.ArrayList<>();
            fields.add(date);
            fields.add(site);
            fields.add(desktopScore);
            for (String m : desktopMetrics) fields.add(m);
            fields.add(desktopURL);
            fields.add(mobileScore);
            for (String m : mobileMetrics) fields.add(m);
            fields.add(mobileURL);

            StringBuilder row = new StringBuilder();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) row.append(",");
                row.append(csvEscape(fields.get(i)));
            }
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
            String chromeBinary = System.getenv("CHROME_BINARY");
            if (chromeBinary != null && !chromeBinary.trim().isEmpty()) {
                options.setBinary(chromeBinary.trim());
                System.out.println("Using Chrome binary: " + chromeBinary.trim());
            }
            boolean ci = "true".equalsIgnoreCase(System.getenv("CI"))
                    || "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
            String display = System.getenv("DISPLAY");
            boolean hasDisplay = display != null && !display.isBlank();
            // Headless Chrome on GitHub leaves PageSpeed on the spinner. xvfb sets DISPLAY
            // so this session is a real window on a virtual screen.
            boolean headless = ci && !hasDisplay;
            options.setExperimentalOption("excludeSwitches", java.util.List.of("enable-automation"));
            options.addArguments("--disable-blink-features=AutomationControlled");
            if (ci) {
                options.addArguments(
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--window-size=1920,1080",
                        "--hide-scrollbars",
                        "--no-first-run",
                        "--disable-extensions");
            }
            if (headless) {
                options.addArguments("--headless=new", "--disable-gpu");
                System.out.println("Chrome mode: headless");
            } else if (ci) {
                System.out.println("Chrome mode: headed on display " + display);
            } else {
                options.addArguments("--start-maximized");
            }

            driver = new ChromeDriver(options);
            driver.manage().window().setSize(new Dimension(1920, 1080));
            wait = new WebDriverWait(driver, Duration.ofSeconds(90));
            if (driver instanceof ChromeDriver) {
                Capabilities caps = ((ChromeDriver) driver).getCapabilities();
                System.out.println("Launched " + caps.getBrowserName() + " " + caps.getBrowserVersion());
            }

            driver.get("https://pagespeed.web.dev/");
            Thread.sleep(2000);
            dismissCookieBanner(driver);

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
            System.out.println("Desktop score: " + desktopScore);
            if ("N/A".equals(desktopScore)) {
                System.out.println("Desktop report still loading after " + (SCORE_WAIT_MS / 1000) + "s — not uploading a screenshot");
                saveDebugScreenshot(driver, sanitize(site) + "_desktop");
            } else {
                ensureTabActive(driver, wait, "desktop_tab");
                desktopMetrics = extractCoreWebVitals(driver);
                scrollToReport(driver);
                desktopURL = takeSSAndUpload(driver, sanitize(site) + "_desktop");
            }

            // MOBILE
            selectTab(wait, "mobile_tab");
            mobileScore = waitForScore(driver, "mobile_tab");
            System.out.println("Mobile score: " + mobileScore);
            if ("N/A".equals(mobileScore)) {
                System.out.println("Mobile report still loading after " + (SCORE_WAIT_MS / 1000) + "s — not uploading a screenshot");
                saveDebugScreenshot(driver, sanitize(site) + "_mobile");
            } else {
                ensureTabActive(driver, wait, "mobile_tab");
                mobileMetrics = extractCoreWebVitals(driver);
                scrollToReport(driver);
                mobileURL = takeSSAndUpload(driver, sanitize(site) + "_mobile");
            }

            if ("N/A".equals(desktopScore) || "N/A".equals(mobileScore)) {
                System.out.println("⚠ Incomplete for: " + site);
            } else {
                System.out.println("✔ Completed for: " + site);
            }

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
        // Poll (like waitForScore does) instead of reading once: the metric value
        // often isn't populated yet at the exact moment the score gauge appears,
        // and only a *visible* element's text should be trusted in case a hidden
        // duplicate (e.g. the other tab's markup) shares the same id.
        long end = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < end) {
            try {
                java.util.List<WebElement> matches =
                        driver.findElements(By.cssSelector("#" + metricId + " .lh-metric__value"));
                for (WebElement el : matches) {
                    if (el.isDisplayed()) {
                        String txt = el.getText().trim();
                        if (!txt.isEmpty()) {
                            // normalize the non-breaking space Lighthouse uses (e.g. "2.6 s")
                            return txt.replace('\u00A0', ' ');
                        }
                    }
                }
            } catch (Exception ignore) {}
            try { Thread.sleep(500); } catch (Exception ignore) {}
        }
        return "N/A";
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

    private static void dismissCookieBanner(WebDriver driver) {
        try {
            java.util.List<WebElement> buttons = driver.findElements(By.xpath(
                    "//*[self::button or self::a][contains(normalize-space(),'Ok, Got it') or contains(normalize-space(),'Got it')]"));
            for (WebElement button : buttons) {
                if (button.isDisplayed()) {
                    button.click();
                    Thread.sleep(400);
                    return;
                }
            }
        } catch (Exception ignore) {}
    }

    /**
     * Report is ready when a performance score and First Contentful Paint are both in the DOM.
     * Selenium's isDisplayed()/getText() miss the gauge when it sits in a shadow root or iframe,
     * which is why a finished report was reported as N/A.
     */
    private static final String READ_REPORT_JS = """
            function norm(s) { return (s || '').replace(String.fromCharCode(160), ' ').trim(); }
            function shown(el) {
              if (!el || !el.getClientRects || !el.getClientRects().length) return false;
              var view = el.ownerDocument && el.ownerDocument.defaultView;
              if (!view) return true;
              var s = view.getComputedStyle(el);
              return s.display !== 'none' && s.visibility !== 'hidden';
            }
            function digits(el) {
              var t = norm(el.innerText || el.textContent);
              if (/^\\d{1,3}$/.test(t)) return t;
              var aria = norm(el.getAttribute && el.getAttribute('aria-label'));
              var m = aria.match(/(\\d{1,3})/);
              return m ? m[1] : '';
            }
            var acc = {score: '', fcp: ''};
            function scan(root) {
              if (!root || !root.querySelectorAll) return;
              if (!acc.score) {
                var perf = root.querySelectorAll('#performance .lh-exp-gauge__percentage, #performance .lh-gauge__percentage');
                for (var p = 0; p < perf.length; p++) {
                  if (!shown(perf[p])) continue;
                  var n = digits(perf[p]);
                  if (n) { acc.score = n; break; }
                }
              }
              if (!acc.fcp) {
                var metrics = root.querySelectorAll('#first-contentful-paint .lh-metric__value');
                for (var k = 0; k < metrics.length; k++) {
                  if (!shown(metrics[k])) continue;
                  var mt = norm(metrics[k].innerText || metrics[k].textContent);
                  if (mt) { acc.fcp = mt; break; }
                }
              }
              var nodes = root.querySelectorAll('*');
              for (var j = 0; j < nodes.length; j++) {
                if (nodes[j].shadowRoot) scan(nodes[j].shadowRoot);
              }
              var frames = root.querySelectorAll('iframe');
              for (var f = 0; f < frames.length; f++) {
                try { if (frames[f].contentDocument) scan(frames[f].contentDocument); } catch (e) {}
              }
            }
            scan(document);
            return JSON.stringify(acc);
            """;

    private static String jsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return "";
        start += key.length();
        int end = json.indexOf('"', start);
        if (end < 0) return "";
        return json.substring(start, end);
    }

    private static String waitForScore(WebDriver driver, String tabId) {
        long end = System.currentTimeMillis() + SCORE_WAIT_MS;
        long nextLog = System.currentTimeMillis() + 20000;
        boolean nudgedTab = false;

        while (System.currentTimeMillis() < end) {
            try {
                WebElement tab = driver.findElement(By.id(tabId));
                if (!nudgedTab && !"true".equals(tab.getAttribute("aria-selected"))) {
                    tab.click();
                    nudgedTab = true;
                    Thread.sleep(1500);
                    continue;
                }

                Object raw = ((JavascriptExecutor) driver).executeScript(READ_REPORT_JS);
                String json = raw == null ? "" : raw.toString();
                String score = jsonField(json, "score");
                String fcp = jsonField(json, "fcp");
                if (score.matches("\\d{1,3}") && !fcp.isEmpty()) {
                    System.out.println(tabId + " ready. FCP=" + fcp);
                    return score;
                }
                if (System.currentTimeMillis() >= nextLog) {
                    System.out.println("Waiting for " + tabId + " metrics... score="
                            + (score.isEmpty() ? "none" : score)
                            + " fcp=" + (fcp.isEmpty() ? "none" : fcp)
                            + " url=" + driver.getCurrentUrl());
                    nextLog = System.currentTimeMillis() + 20000;
                }
            } catch (Exception ignore) {}
            try { Thread.sleep(700); } catch (Exception ignore) {}
        }
        try {
            System.out.println("Timed out on " + tabId + ". Page title: " + driver.getTitle());
            Object text = ((JavascriptExecutor) driver).executeScript(
                    "return (document.body && document.body.innerText || '').replace(/\\s+/g, ' ').slice(0, 400);");
            System.out.println("Page text: " + text);
        } catch (Exception ignore) {}
        return "N/A";
    }

    private static void saveDebugScreenshot(WebDriver driver, String name) {
        try {
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Path dest = Path.of("target", "pagespeed-debug-" + name + ".png");
            Files.createDirectories(dest.getParent());
            Files.write(dest, png);
            System.out.println("Saved debug screenshot: " + dest);
        } catch (Exception e) {
            System.out.println("Debug screenshot failed: " + e.getMessage());
        }
    }

    /**
     * Scrolls the Core Web Vitals block (FCP, LCP, TBT, CLS, Speed Index) into the screenshot.
     * The category gauges at the top of the report are a different section.
     */
    private static void scrollToReport(WebDriver driver) {
        try {
            WebElement metric = null;
            for (WebElement el : driver.findElements(By.id("first-contentful-paint"))) {
                if (el.isDisplayed()) {
                    metric = el;
                    break;
                }
            }
            if (metric == null) {
                return;
            }
            ((JavascriptExecutor) driver).executeScript(
                    "var metric = arguments[0];" +
                    "var group = metric.closest('.lh-audit-group') || metric;" +
                    "var target = group;" +
                    "var prev = group.previousElementSibling;" +
                    "if (prev) {" +
                    "  var h = prev.getBoundingClientRect().height;" +
                    "  if (h > 0 && h < 320) target = prev;" +
                    "}" +
                    "target.scrollIntoView({behavior:'auto', block:'start'});" +
                    "var tabs = document.getElementById('desktop_tab') || document.getElementById('mobile_tab');" +
                    "var sticky = 0;" +
                    "if (tabs) {" +
                    "  var bar = tabs.closest('[role=tablist]') || tabs.parentElement;" +
                    "  if (bar) sticky = Math.max(0, bar.getBoundingClientRect().bottom);" +
                    "}" +
                    "window.scrollBy(0, -(sticky + 16));",
                    metric);
            Thread.sleep(1200);
        } catch (Exception ignore) {}
    }

    private static String takeSSAndUpload(WebDriver driver, String filename) {
        try {
            byte[] data = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Path dest = Path.of("target", filename + ".png");
            Files.createDirectories(dest.getParent());
            Files.write(dest, data);
            System.out.println("Saved screenshot: " + dest.toAbsolutePath());
            return upload(data, filename);
        } catch (Exception e) {
            System.out.println("Screenshot failed: " + e.getMessage());
            return "FAILED";
        }
    }

    private static String upload(byte[] img, String filename) {
        try {
            String base64 = Base64.getEncoder().encodeToString(img);
            String url = "https://api.imgbb.com/1/upload?key=" + API_KEY;

            HttpsURLConnection conn = (HttpsURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");

            String data = "image=" + URLEncoder.encode(base64,"UTF-8")
                    + "&name=" + URLEncoder.encode(filename,"UTF-8");

            conn.getOutputStream().write(data.getBytes());

            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            String res = br.readLine();
            br.close();
            if (code >= 400 || res == null) {
                System.out.println("Upload failed: HTTP " + code + " " + res);
                return "FAILED";
            }

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
