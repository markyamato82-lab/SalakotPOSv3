# SalakotPOS v3

Restaurant point of sale for the Philippines: tables and open orders, item options, kitchen display,
senior citizen / PWD discounts (VAT-exempt, pro-rated per diner), service charge, split payments
(cash, GCash, Maya, card), BIR-style receipts, reports, staff roles with 4-digit PIN sign-in,
manager PIN approvals, and Epson ePOS-Print network printers.

Backend: Supabase (Postgres with row-level security; all business logic in database functions).

## What's in this repository

| Path | What it is |
| --- | --- |
| `index.html` | The **launcher**. Deploy this to Netlify. It loads the latest app version from Supabase (`app_releases` table) every time it opens, so app updates need no redeploy. |
| `standalone/salakot-pos.html` | The whole app as one file. Works without the launcher; useful as a backup. Does not auto-update. |
| `src/` | App source: styles and JavaScript, split into parts. |
| `vendor/` | Bundled `@supabase/supabase-js` 2.117.2 (MIT). |
| `build.sh` | Rebuilds `standalone/salakot-pos.html` from `src/`. |
| `supabase/functions/pin-login/` | Edge function for PIN sign-in. |

The database schema and functions live in the Supabase project (Database → Migrations).

## Deploying

**Netlify:** connect this repository (publish directory: repository root), or drag a folder containing `index.html` onto the site's Deploys page.
Make sure Visitor access does not require a Netlify login for the production site.

**Rebuild the standalone file after editing `src/`:**

```sh
./build.sh
```

## Notes

- The Supabase URL and publishable key in the code are meant to be public; security comes from row-level security and permission checks inside the database functions.
- Never commit service-role keys, passwords or PINs to this repository.
- Printing to network printers needs Epson ePOS-Print, HTTPS, and Chrome's local network access permission. Other printers work through the print dialog.
