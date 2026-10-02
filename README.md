# Money Home Made Simple

A USSD money-transfer demo for sending Rand (ZAR) home to Zimbabwe, received as US dollars.
It runs on a basic phone with one bar of signal: no app, no data, just a dial code.

- English and Shona screens, language remembered per phone number
- Quote shown before anything is sent: fee, rate, "they get", "you pay", and how long the rate is held
- Rate lock (10 minutes) so the number you confirmed is the number you pay
- Optional PIN step (the first PIN a phone enters becomes its PIN; locks after repeated wrong tries)
- Safe against double-submits: confirming twice with the same `sessionId` creates one transfer
- Tracking by reference (`RM-XXXXXX`): Sent, In transit, Ready to collect, Collected
- SMS simulator for sender and receiver on every status change
- Monthly payments (backend only, see "Not built yet")

## Run it

Needs a JDK that matches `pom.xml` (currently Java 25).

```
mvn test                       # run the tests
```

Run `za.hack.remit.Main` from IntelliJ (right-click, Run). The server starts on port 7070.
For a terminal-only walkthrough with no server, run `za.hack.remit.ussd.dev.ConsoleDemo`.

Settings (all optional environment variables):

| Variable         | Default      | What it does                                                        |
|------------------|--------------|---------------------------------------------------------------------|
| `PORT`           | `7070`       | HTTP port                                                           |
| `ADMIN_KEY`      | random       | Key for the admin endpoints. A random one is printed at startup.    |
| `GATEWAY_SECRET` | not set      | If set, `POST /ussd` must send header `X-Gateway-Secret` with it    |
| `USE_PIN`        | on           | `false` turns the PIN step off (faster demo)                        |
| `STEP_SECONDS`   | `20`         | Seconds between automatic status steps                              |

## The `/ussd` contract

`POST /ussd`, form-encoded, the same shape Africa's Talking uses:

| Field         | Meaning                                                    |
|---------------|------------------------------------------------------------|
| `sessionId`   | Gateway session id                                         |
| `phoneNumber` | The caller (required, 400 if missing)                      |
| `text`        | The keys pressed so far, joined with `*`, e.g. `1*500*1`   |

The reply is `text/plain` and always starts with `CON ` (keep the session open) or `END ` (finished).
The server keeps the journey state per phone number and only reads the last item of `text`.
Every screen should stay at 160 characters or fewer; `MessagesTest` checks this.

## Other endpoints

| Endpoint                 | Auth            | Purpose                                                    |
|--------------------------|-----------------|------------------------------------------------------------|
| `GET /health`            | none            | Returns `ok`                                               |
| `GET /api/rate`          | none            | Current ZAR to USD rate and the mid-market rate            |
| `GET /api/sms`           | none            | Recent simulated SMS messages (phone numbers masked)       |
| `GET /api/transfers`     | `X-Admin-Key`   | All transfers, newest first (phone numbers masked)         |
| `POST /admin/advance`    | `X-Admin-Key`   | Form field `reference`: move one transfer one step         |
| `POST /admin/date`       | `X-Admin-Key`   | Form field `days` (1 to 31): move the demo calendar, run the monthly check |
| `GET /api/demo-date`     | none            | The demo calendar's "today"                                |

## Money rules

All in `fees/DefaultQuoteCalculator.java`, in one place:

- Amount: R50 to R5000
- Fee: 5% of the amount, at least R15 and at most R100
- Rate: starts from a base (R17.80 if the live fetch fails), drifts within +/-2%, and the customer's rate includes a 1% margin
- All maths uses `BigDecimal`. The USSD screen, SMS and records all read from the same `Quote`.

## Code layout

| Package      | What lives there                                                      |
|--------------|-----------------------------------------------------------------------|
| `ussd`       | Controller, session store, screens, validation, `UssdFactory`         |
| `fx`         | `FxService`, `MockFxService` (moving rate), `RateLock`                |
| `fees`       | `QuoteCalculator`, `Quote`, fee and limit rules                       |
| `transfer`   | `Transfer`, status rules, `TransferService`, auto-advancing scheduler |
| `notify`     | `SmsSimulator`, sender and receiver status messages                   |
| `recurring`  | Monthly plans (`RecurringService`) and the demo calendar (`DemoDate`) |
| `security`   | PIN service, rate limiter, phone-number masking                       |
| `admin`      | Admin and demo HTTP endpoints                                         |
| `i18n`       | `Messages` (English and Shona), GSM-7 helper                          |

Message keys live in `src/main/resources/messages_en.properties` and `messages_sn.properties`.
Both files must have exactly the same keys (a test enforces it). When adding a key, add it to both.

## Not built yet

- The browser simulator (`src/main/resources/simulator`) is a standalone mock. It does not call `/ussd`
  or any other endpoint, and it still shows Kenyan shillings and a +254 number. Until it is rewired,
  drive the real server with `curl` or a gateway, or use `ConsoleDemo`.
- No USSD screen for monthly payments. `RecurringService` sends the reminder and the transfer, but
  its SMS says to dial `*120*3#`, and menu item 3 is Language.
- No "My recipients" screen.
- `0` for back and `00` for home are not implemented on every screen.
- Shona text still needs a native speaker's review before a real demo.
