#!/usr/bin/env bash
# Usage: cd into your cloned repo, run `gh auth login` once, then: bash create-issues.sh
# Optional: assign people after creation (e.g. gh issue edit N --add-assignee name)
set -e

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
echo "Creating issues in $REPO"

# ---------- Labels ----------
label() { gh label create "$1" --color "$2" --description "$3" --force >/dev/null; }
label "owner:1-lead"      "1d76db" "Person 1: skeleton, wiring, integration"
label "owner:2-ussd"      "0e8a16" "Person 2: USSD flow and sessions"
label "owner:3-money"     "fbca04" "Person 3: FX, fees, quotes"
label "owner:4-transfers" "d93f0b" "Person 4: status machine, scheduler, SMS"
label "owner:5-demo"      "5319e7" "Person 5: simulator, i18n, demo"
label "stretch"           "c5def5" "Nice to have, only if ahead of schedule"
label "blocker"           "b60205" "Blocks other people"

# ---------- Milestones ----------
ms() { gh api "repos/$REPO/milestones" -f title="$1" -f description="$2" >/dev/null 2>&1 || true; }
ms "M0 Skeleton (hour ~1)"        "Compiling project with interfaces and stubs on main"
ms "M1 First end-to-end (hour ~4)" "Amount -> quote -> confirm -> reference works, even if rough"
ms "M2 Feature complete (hour ~6)" "Full flow, tracking, SMS, both languages. FEATURE FREEZE"
ms "M3 Demo ready (final 1-2h)"    "Bug fixes, run-throughs, README, pitch. No new features"

# ---------- Issue helper: title | milestone | labels | body (stdin) ----------
mk() {
  local body; body=$(cat)
  gh issue create --title "$1" --milestone "$2" --label "$3" --body "$body"
}

M0="M0 Skeleton (hour ~1)"
M1="M1 First end-to-end (hour ~4)"
M2="M2 Feature complete (hour ~6)"
M3="M3 Demo ready (final 1-2h)"

# =====================================================================
# PERSON 1: LEAD / INTEGRATION
# =====================================================================
mk "Project skeleton: Maven, Java 21, Javalin, /ussd echo" "$M0" "owner:1-lead,blocker" <<'EOF'
**Owner:** Person 1 | **Due:** hour ~1

Everyone needs a compiling project before they can start.

- [ ] `pom.xml` with Java 21, Javalin, JUnit 5, slf4j
- [ ] `Main.java` starts the server on port 7070
- [ ] `POST /ussd` accepts `sessionId`, `phoneNumber`, `text` (form-encoded) and echoes `CON ...`
- [ ] `.gitignore` (target/, .idea/, *.iml)
- [ ] Everyone confirms it runs in IntelliJ
- [ ] Only the lead edits `pom.xml` from here on (others ask in chat)
EOF

mk "Shared interfaces, model classes and stub implementations" "$M0" "owner:1-lead,blocker" <<'EOF'
**Owner:** Person 1 | **Due:** hour ~1

So Persons 2-5 can build against stubs without waiting on each other.

- [ ] `FxService` (`lockRate`, `currentRate`) + `RateLock` record + hardcoded stub
- [ ] `QuoteCalculator` (`quote(amountZar, RateLock)`) + `Quote` record + hardcoded stub
- [ ] `TransferService` (`create`, `advance`, `find`, `forPhone`) + `Transfer` + in-memory stub
- [ ] `TransferStatus` enum (SENT, IN_TRANSIT, READY_TO_COLLECT, COLLECTED, CANCELLED, FAILED)
- [ ] All money fields are `BigDecimal`
- [ ] Merged to main, announced in chat
EOF

mk "Team contracts + repo rules (CONTRIBUTING.md, branch protection)" "$M0" "owner:1-lead,blocker" <<'EOF'
**Owner:** Person 1 | **Due:** hour ~1

- [ ] Branch protection on `main` (PR required, 1 review)
- [ ] `CONTRIBUTING.md`: branch naming (`feat/...`), small PRs, merge to main every 1-2h, rebase before each chunk, one file one owner
- [ ] Document the `/ussd` contract (CON/END, `text` joined by `*`)
- [ ] Document message key naming (`menu.main`, `quote.title`, `error.min_amount`)
- [ ] Document package ownership table
EOF

mk "Wire real implementations into Main (replace stubs)" "$M1" "owner:1-lead" <<'EOF'
**Owner:** Person 1 | **Due:** hour ~4

- [ ] Swap each stub for the real implementation as it lands
- [ ] Start FX ticker and transfer scheduler on boot
- [ ] Register `/admin/advance`, `/api/rate`, `/api/sms` routes
- [ ] Config values (fee, minimum, rate lock minutes) in one place
EOF

mk "Integration run-through and bug bash" "$M3" "owner:1-lead" <<'EOF'
**Owner:** Person 1 | **Due:** final 1-2h

- [ ] Fresh clone builds and runs from README instructions only
- [ ] Full demo script run at least twice with the whole team
- [ ] Triage and assign every open bug; no new features
- [ ] Tag a `demo` release on main
EOF

# =====================================================================
# PERSON 2: USSD FLOW
# =====================================================================
mk "UssdSession and language remembered per phone number" "$M1" "owner:2-ussd" <<'EOF'
**Owner:** Person 2 | **Due:** hour ~4

- [ ] `UssdSession` holds language, step and draft transfer
- [ ] Language chosen once (1 English / 2 Shona) and stored per phone number
- [ ] Returning users skip the language screen
- [ ] Session expires after inactivity
EOF

mk "Main menu and navigation (back, home, exit)" "$M1" "owner:2-ussd" <<'EOF'
**Owner:** Person 2 | **Due:** hour ~4

Menu: 1 Send money, 2 Track money, 3 My recipients.

- [ ] Parse `text` split on `*` to find the current screen
- [ ] `0` goes back, `00` goes home (consistent on every screen)
- [ ] Every response is `CON ...` or `END ...`
EOF

mk "Send flow: amount -> recipient -> quote -> PIN -> reference" "$M1" "owner:2-ussd,blocker" <<'EOF'
**Owner:** Person 2 | **Due:** hour ~4

The core journey. Use the stubs first, swap in real services later.

- [ ] Amount screen (rand)
- [ ] Recipient screen (saved recipient or new number)
- [ ] Quote screen: fee in rand, rate, "Mama gets", "You pay", rate-held time, 1 Confirm / 2 Cancel
- [ ] PIN screen
- [ ] END screen with reference number
- [ ] Nothing is added after the confirm step
EOF

mk "Input validation and error screens" "$M2" "owner:2-ussd" <<'EOF'
**Owner:** Person 2 | **Due:** hour ~6

- [ ] Invalid amount (non-number, zero, negative)
- [ ] Below minimum / above maximum
- [ ] Wrong PIN (limit attempts)
- [ ] Rate expired: re-quote with the new rate and ask again
- [ ] Unknown reference when tracking
- [ ] All errors use message keys from Messages, never hardcoded text
EOF

mk "Track money screen" "$M2" "owner:2-ussd" <<'EOF'
**Owner:** Person 2 | **Due:** hour ~6

- [ ] List the sender's recent transfers (last 3-5)
- [ ] Detail view by reference showing Sent -> In transit -> Ready to collect -> Collected
- [ ] Shows amount, receiver gets, and time of last update
EOF

mk "Resume dropped session and idempotent confirm" "$M2" "owner:2-ussd" <<'EOF'
**Owner:** Person 2 | **Due:** hour ~6

Flaky signal is the whole brief. This is a great demo moment.

- [ ] If the user redials within N minutes, offer "1 Continue where you left off / 2 Start over"
- [ ] Confirm is keyed on `sessionId`, so a double-submit never sends twice
- [ ] Test: replay the same confirm request and verify one transfer exists
EOF

mk "My recipients screen" "$M2" "owner:2-ussd,stretch" <<'EOF'
**Owner:** Person 2

- [ ] List saved recipients
- [ ] Add a new recipient (name + number)
- [ ] Pick from the list during the send flow
EOF

# =====================================================================
# PERSON 3: MONEY CORE
# =====================================================================
mk "FxService: moving mock rate (random walk, clamped +/-2%)" "$M1" "owner:3-money" <<'EOF'
**Owner:** Person 3 | **Due:** hour ~4

- [ ] ZAR -> USD base rate (e.g. 17.80)
- [ ] Scheduled tick every few seconds, random walk, clamped within +/-2% of base
- [ ] `currentRate(from, to)`
- [ ] Thread-safe
- [ ] `GET /api/rate` for the simulator to display (coordinate with Person 1)
EOF

mk "Rate lock with expiry" "$M1" "owner:3-money,blocker" <<'EOF'
**Owner:** Person 3 | **Due:** hour ~4

- [ ] `lockRate(from, to)` returns `RateLock(rate, expiresAt)` (10 min)
- [ ] `isExpired()` helper
- [ ] Transfer uses the locked rate, never the live one
EOF

mk "QuoteCalculator (BigDecimal, single source of truth)" "$M1" "owner:3-money,blocker" <<'EOF'
**Owner:** Person 3 | **Due:** hour ~4

- [ ] `quote(amountZar, RateLock)` returns fee, rate, receiver gets (USD), total to pay
- [ ] Fee rule agreed with team (flat R25 or tiered) and documented
- [ ] `BigDecimal` + `RoundingMode.HALF_UP`, explicit scale everywhere
- [ ] The USSD screen, SMS and ledger all read from `Quote` and never recalculate
EOF

mk "Unit tests for quote and FX maths" "$M2" "owner:3-money" <<'EOF'
**Owner:** Person 3 | **Due:** hour ~6

- [ ] Known cases: R500 -> expected fee, receiver amount, total
- [ ] Rounding edge cases
- [ ] Rate always stays within +/-2% over many ticks
- [ ] Expired lock detected
- [ ] Min / max amount boundaries
EOF

mk "Show mid-market rate and margin on quote/SMS" "$M2" "owner:3-money,stretch" <<'EOF'
**Owner:** Person 3

Extra transparency credit: show the mid-market rate next to our rate.

- [ ] `Quote` exposes mid-market rate
- [ ] Coordinate with Person 5 for a message key and with Person 4 for the SMS receipt
EOF

# =====================================================================
# PERSON 4: TRANSFERS
# =====================================================================
mk "Transfer model, TransferStatus state machine and TransferService" "$M1" "owner:4-transfers,blocker" <<'EOF'
**Owner:** Person 4 | **Due:** hour ~4

- [ ] `Transfer` (id/reference, phone, recipient, quote, status, timestamps, status history)
- [ ] `TransferStatus.canMoveTo` rules; `advance(id)` throws on invalid transitions
- [ ] In-memory `TransferService`: `create`, `advance`, `find`, `forPhone`
- [ ] Short, readable reference numbers (e.g. `TH4821`)
EOF

mk "Scheduler: auto-advance transfers every 10-20 seconds" "$M2" "owner:4-transfers" <<'EOF'
**Owner:** Person 4 | **Due:** hour ~6

- [ ] `ScheduledExecutorService` moves SENT -> IN_TRANSIT -> READY_TO_COLLECT
- [ ] COLLECTED only via admin action (demo control)
- [ ] Configurable interval; can be paused for live demos
EOF

mk "SmsSimulator: receipt and 'ready to collect' notifications" "$M2" "owner:4-transfers" <<'EOF'
**Owner:** Person 4 | **Due:** hour ~6

- [ ] Receipt SMS on confirm: reference, fee, rate, total, receiver gets
- [ ] Receiver SMS when status becomes READY_TO_COLLECT: "Your money is ready to collect"
- [ ] Stores messages in memory; `GET /api/sms` for the simulator's SMS panel
- [ ] SMS language follows the user's language
EOF

mk "/admin/advance endpoint and idempotency tests" "$M2" "owner:4-transfers" <<'EOF'
**Owner:** Person 4 | **Due:** hour ~6

- [ ] `POST /admin/advance/{id}` for driving the demo live
- [ ] Clear error if the transition is invalid
- [ ] Tests: every valid and invalid transition, duplicate `create` with the same sessionId returns the same transfer
EOF

# =====================================================================
# PERSON 5: SIMULATOR, I18N, DEMO
# =====================================================================
mk "Browser phone simulator (keypad + screen)" "$M1" "owner:5-demo,blocker" <<'EOF'
**Owner:** Person 5 | **Due:** hour ~4

This is what judges see.

- [ ] One HTML page in `src/main/resources/public/`, styled like a basic feature phone
- [ ] Dial `*120#`, then keypad input, send/reply, cancel
- [ ] POSTs `sessionId`, `phoneNumber`, `text` to `/ussd`; displays CON/END text
- [ ] Text accumulates with `*` exactly like Africa's Talking
- [ ] Works on a laptop projector and on a phone browser
EOF

mk "Messages class and en/sn properties files" "$M1" "owner:5-demo,blocker" <<'EOF'
**Owner:** Person 5 | **Due:** hour ~4

- [ ] `Messages.get(key, lang, args...)`
- [ ] `messages_en.properties` and `messages_sn.properties`
- [ ] Plain words: "Mama gets", "fee", "You pay"
- [ ] Others send new keys in chat or a small PR (this file is a conflict hotspot)
EOF

mk "Test: every USSD screen is 160 characters or fewer in both languages" "$M2" "owner:5-demo" <<'EOF'
**Owner:** Person 5 | **Due:** hour ~6

- [ ] Render every screen with worst-case values (large amount, long recipient name)
- [ ] Assert length <= 160 for English and Shona
- [ ] Fails the build when someone adds a long string
EOF

mk "Admin / SMS panel on the simulator page" "$M2" "owner:5-demo" <<'EOF'
**Owner:** Person 5 | **Due:** hour ~6

- [ ] Panel showing live FX rate (from `/api/rate`)
- [ ] Transfer list with an "Advance" button per transfer (calls `/admin/advance`)
- [ ] SMS inbox showing receipts and ready-to-collect messages
EOF

mk "Shona strings reviewed by a native speaker" "$M3" "owner:5-demo,blocker" <<'EOF'
**Owner:** Person 5 | **Due:** before the demo

A wrong phrase on a money screen kills trust.

- [ ] Find a native Shona speaker (family, friends, campus)
- [ ] Review every money-related screen and SMS
- [ ] Update `messages_sn.properties` and re-run the 160-char test
EOF

mk "README, demo script and pitch" "$M3" "owner:5-demo" <<'EOF'
**Owner:** Person 5 | **Due:** final 1-2h

- [ ] README: what it is, how to run, architecture, package ownership
- [ ] Demo script (3-5 min): Thandi dials *120#, sends R500, sees fees, drops signal and resumes, tracks the money, receiver gets the SMS
- [ ] Pitch: why USSD fits a R200 phone with one bar of signal; transparency story; ready for Africa's Talking
- [ ] Backup plan if the live demo breaks (screen recording)
EOF

echo "Done. Review at: https://github.com/$REPO/issues"
