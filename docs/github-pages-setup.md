# GitHub Pages Setup (Back to Safety)

This repo now includes a Pages site under `site/` and a deployment workflow in `.github/workflows/deploy-pages.yml`.

## What is deployed

- Home page: `/` (`site/index.html`)
- Privacy policy page: `/privacy.html` (`site/privacy.html`)

## Enable GitHub Pages

1. Go to repository Settings -> Pages.
2. Under Source, choose **GitHub Actions**.
3. Push to `main` (or run the workflow manually).
4. Wait for `Deploy GitHub Pages` workflow to finish.

## Configure Custom Domain Later

When you are ready to use your real domain:

1. Copy `site/CNAME.example` to `site/CNAME` and replace with your real domain if needed.
2. In your DNS provider, add:
   - `A` records for apex domain to GitHub Pages IPs, or
   - `CNAME` record for `www` to `<your-github-username>.github.io`.
3. In Settings -> Pages, set the custom domain and enable HTTPS.
4. Verify:
   - `https://<your-domain>/`
   - `https://<your-domain>/privacy.html`

## Store Submission URLs

- Privacy Policy URL should point to your deployed privacy page (recommended: `https://<your-domain>/privacy.html` or `/privacy`).
- Support URL can point to your site root until a dedicated support page is added.
