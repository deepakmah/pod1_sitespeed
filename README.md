# PageSpeed Automation

Runs PageSpeed (web.dev) checks on a list of websites, saves results to CSV, and can email the report.

## Run via GitHub Actions (recommended)

1. **Push this repo to GitHub** (create a new repo and push, or use an existing one).

2. **Add repository secrets** (Settings → Secrets and variables → Actions):
   - `SMTP_USER` – your SMTP login (e.g. Gmail address)
   - `SMTP_PASSWORD` – SMTP password (for Gmail use an [App Password](https://support.google.com/accounts/answer/185833))
   - `EMAIL_TO` – email address that should receive the CSV

3. **Optional repository variables** (Settings → Secrets and variables → Actions → Variables):
   - `SMTP_SERVER` – default `smtp.gmail.com`
   - `SMTP_PORT` – default `587`

4. **Schedule and manual run**
   - The workflow runs **every Monday at 6:00 AM UTC** (edit `.github/workflows/pagespeed.yml` and the `schedule` cron to change it).
   - **To run manually:** Go to your repo on GitHub → open the **Actions** tab → in the **left sidebar** under "All workflows" click **"Page Speed Report"** (or **"pagespeed"** — the workflow from `.github/workflows/pagespeed.yml`) → on the right click **"Run workflow"** → choose branch **main** → click the green **"Run workflow"** button.

   **If you don’t see the workflow in the sidebar:** GitHub only lists workflows that exist on the **default branch**. Set the default branch to **main**: **Settings** → **General** → **Default branch** → switch to **main** → **Save**. Then open the **Actions** tab again; "Page Speed Report" or "pagespeed" should appear in the left sidebar.

5. **Get the CSV**
   - **Email:** If secrets are set, the CSV is sent as an attachment to `EMAIL_TO` after each run.
   - **Artifact:** Every run uploads the CSV as an artifact. Open the run → **Artifacts** → download `pagespeed-results-<run_number>`.

## Run locally

- **Windows:**  
  `mvn compile exec:java -Dexec.mainClass="Speed.sitepeed"`  
  CSV path: `%USERPROFILE%\Documents\pagespeed_results.csv` (or set `PAGESPEED_CSV_PATH`).

- **Linux/macOS:**  
  `mvn compile exec:java -Dexec.mainClass="Speed.sitepeed"`  
  CSV path: `~/pagespeed_results.csv` (or set `PAGESPEED_CSV_PATH`).

## Cron (schedule) on GitHub

The schedule is defined in `.github/workflows/pagespeed.yml`:

```yaml
schedule:
  - cron: '0 6 * * 1'   # Every Monday 06:00 UTC
```

Examples:

- Every day at 7 AM UTC: `0 7 * * *`
- Every Monday and Thursday at 6 AM: `0 6 * * 1,4`
- [Cron syntax](https://crontab.guru/) (minute hour day-of-month month day-of-week).

## Notes

- **ImgBB API key** is in `sitepeed.java`; consider moving it to a secret or env var if the repo is public.
- **Chrome:** GitHub Actions uses headless Chrome automatically. Locally, Chrome must be installed; the script uses it in headed mode unless `CI=true` or `GITHUB_ACTIONS=true` is set.
