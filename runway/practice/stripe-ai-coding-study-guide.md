# Stripe AI Programming Exercise: 3-Day Study Guide (Java)
**Role:** Security Engineer, Abuse Control · **Round:** 60 min, HackerRank AI Coding Environment, built-in AI assistant

> **Where these problems come from:** public GitHub repos and interview write-ups (sources at the bottom). The most-reported problem for this round is a **transaction rule parser** (accept/block rules over a list of transactions, building up to AND/OR logic). It's listed first; spend the most time on it.

---

## 0. How to start ANY AI-assisted interview (memorise this, first 8 minutes)

### The HackerRank AI panel
- **Plan mode**: the AI reads the problem and proposes a plan in Markdown *before any code*. Use it first.
- **Ask mode**: questions about the problem or code. **Tag the `problem statement` (and files)** so it has context.
- **Agent mode**: writes and edits files. Each edit shows a pending dialog: **Allow** or **Cancel**. Read every diff before you click Allow.
- **Inline completions** may appear as you type. Accept them only when you've read them.
- You can switch models. Pick one at the start and stick with it.
- Note: in plain "Coding" questions only Ask mode may be available. Check in the first minute which modes you have.

### Opening script (say it out loud)
1. **(1 min) Orient:** "I'll start by reading the README and starter code myself, then have the assistant summarise requirements so we can check we agree."
2. **(2 min) Read it yourself:** README, starter files, the test runner. Find the **entry point** and the **input/output format**.
3. **(1 min) Prompt #1, in Plan or Ask mode:**
   ```
   Read the problem statement and all starter files. Summarise:
   (1) input and output format, (2) each part's requirements,
   (3) ambiguities or edge cases, (4) the entry point and how tests run.
   Don't write code yet.
   ```
4. **(1 min) Clarify with the interviewer:** pick 1–2 ambiguities from the AI's list. "Is the input sorted? Is the boundary inclusive? What happens with malformed rows?"
5. **(2 min) Prompt #2, plan only Part 1:**
   ```
   Propose a minimal design for Part 1 only, in Java 17: classes/records,
   method signatures, data structures. Keep it simple; later parts will extend it.
   ```
   **Critique it out loud:** cut over-engineering, fix types (money as `long` cents), confirm the signatures.
6. **(1 min) Prompt #3, implement:**
   ```
   Implement Part 1 using exactly that design. Don't change the existing
   method signatures or the main/test harness.
   ```
   Review the diff → Allow → run.

### Loop for every later part
**Restate the part → ask the AI for the smallest change to the design → review the diff → add your own edge-case tests → run → narrate → next part.**

### Prompts to keep ready
| Purpose | Prompt |
|---|---|
| Tests | "Write 6 tests for Part N covering boundaries, empty input, and invalid input. Print PASS/FAIL. Don't change the solution code." |
| Critique | "Review this method for bugs and missed edge cases against the spec. List them; don't fix them yet." |
| Debug | "Test X expects A but gets B. Walk through the code with that input and explain why. Don't edit yet." |
| Refactor for next part | "Part N+1 needs <X>. What's the smallest refactor to support it without breaking earlier tests?" |
| Rein in | "That's over-engineered. Remove <interfaces/factories/…> and keep it to one class." |

### Things that hurt you
Asking it to "solve everything" · clicking Allow without reading · silent pauses · rewriting parts that already passed · not running the code until the end.

### Last 5 minutes
Summarise what works, the tests you'd add, known gaps, and how you'd run it in production (state in Redis or a stream processor, config-driven rules, alerting, false-positive review).

---

## 1. ⭐ Transaction Rule Engine (accept/block): MOST LIKELY
**Reported for:** the Stripe AI-assisted round (interviewdb, 1point3acres).
**Problem:** given transactions (fields such as `id, amount, currency, country, card_country, merchant, email…`) and rule definitions, decide **ACCEPT/BLOCK** for each transaction.

**Likely parts**
1. Single-condition rules: `block if country == "XX"`, keyword or string match (`email contains "tempmail"`).
2. Numeric comparisons: `amount > 100000`, operators `== != > < >= <=`, typed comparison (number vs string).
3. **Boolean logic:** `AND` / `OR` (AND binds tighter than OR); maybe parentheses.
4. Rule priority or allow-lists (`allow` overrides `block`), explain *which rule fired*; maybe score thresholds.

**Java design**
```java
record Txn(Map<String,String> f) {}
sealed interface Expr permits Cond, And, Or {}
record Cond(String field, String op, String value) implements Expr {}
record And(Expr l, Expr r) implements Expr {}
record Or(Expr l, Expr r) implements Expr {}
// parse: split on " OR " first → each side split on " AND " → Cond (no parentheses needed)
// with parentheses: recursive descent  expr := term (OR term)* ; term := factor (AND factor)* ; factor := '(' expr ')' | cond
boolean eval(Expr e, Txn t) { return switch (e) { case Cond c -> cmp(c, t); case And a -> eval(a.l(),t) && eval(a.r(),t); case Or o -> eval(o.l(),t) || eval(o.r(),t); }; }
```
**Bugs to catch in AI code:** splitting on `"AND"` inside values (e.g. `"BRANDON"`) → split on `" AND "` or tokenise; comparing numbers as strings (`"9" > "10"`); missing field → null pointer exception; case sensitivity; operator precedence; `>=` matched as `>`.

**Opening prompt:** "Read the README. List the rule grammar exactly as specified (operators, quoting, precedence) and every example with its expected output. Don't code yet."

**Abuse-control talking points:** rules start in shadow/log-only mode; explainability for merchant appeals; attackers probe fixed thresholds.

---

## 2. Fraud Detection: authorisations vs time-stamped rules
**Source:** adonais0.github.io Stripe write-up.
**Problem:** a stream of authorisation requests and fraud rules, each with a timestamp. **A rule applies only to requests at or after its timestamp** ("new rules can't apply to previous requests"). Output approve/decline per request.

**Parts:** (1) one field-equality rule; (2) multiple rules, any match → decline; (3) rule removal or expiry; (4) output summary per merchant.
**Design:** sort the events by time and merge them (rules and requests); keep the active rule set in a `Map`. Ties: decide whether a rule at time T applies to a request at time T, and **ask the interviewer**.
**Opening prompt:** "Summarise the ordering semantics: when exactly does a rule start applying? What happens on equal timestamps?"

---

## 3. Shipping Cost Calculator (tiered pricing)
**Source:** `harry-the-nerd/interview-notes-questions/stripe/shipping-cost-calculator.md`.
**Problem:** order = `country + list of (product, qty)`. Cost config per country and product.

1. **Fixed per unit:** 20 mice × 550 + 5 laptops × 1000 = **16,000**
2. **Tiered incremental**, tiers are **half-open `[min, max)`**, `max = null` means unlimited. Laptops: 2 × 1000 + 3 × 900 → total **15,700**
3. **Mixed types:** each tier is `fixed` (flat fee for the tier) or `incremental` (qty × cost). Laptops: 1000 fixed + 3 × 900 → total **14,700**

**Bugs to catch:** off-by-one at tier edges (`[0,2)` contains 2 units, not 3); unsorted tiers; `null` max; `int` overflow; `double` for money.
**Opening prompt:** "Work through each README example by hand and show the arithmetic tier by tier, so we confirm the interval semantics before coding."

---

## 4. Currency Conversion (graph)
**Source:** adonais0 write-up; codinginterview.com ("Evaluate Division").
**Input:** `"USD:EUR:0.9,EUR:GBP:0.85,…"`.
1. Direct rate (also the inverse, 1/rate).
2. Rate through **one** intermediate currency.
3. Any path (**BFS/DFS**), maybe the **best** rate (max product → Bellman-Ford style, or try every path for small graphs).
4. Every rate pair you can compute.

**Bugs to catch:** cycles (keep a visited set); unknown currency; floating-point formatting; parsing whitespace.
**Opening prompt:** "Parse the input format first and print the adjacency map so I can verify the parse, then stop."

---

## 5. Accept-Language Header Parser
**Source:** adonais0 write-up (phone screen).
1. `parse("en-US, fr-CA, fr-FR", ["fr-FR","en-US"])` → `["en-US","fr-FR"]` (keep the header's order, intersected with what's supported).
2. A bare language `"fr"` matches every `fr-*` that's supported.
3. Wildcard `*` matches everything else supported.
4. **q-factors** `fr;q=0.8`: sort by q, highest first, with a stable tie-break.

**Bugs to catch:** duplicate results, case sensitivity, stable sort, `q=0` meaning "not acceptable".

---

## 6. Store Closing Time / Server Removal Penalty
**Source:** adonais0; LeetCode 2483 (Minimum Penalty for a Shop).
1. `"Y Y N Y"` + closing hour → penalty (+1 for each `N` while open, +1 for each `Y` after closing).
2. Best closing hour (O(n) prefix/suffix count).
3. Parse a log made of `BEGIN … END` blocks, possibly nested or messy → best time for each valid block.
**Bugs to catch:** closing hour range is `0..n` inclusive; parsing tokens across newlines; invalid blocks.

---

## 7. Transaction Reconciliation
**Source:** `sumansaurav91/stripe-interview-prep`.
1. Match the internal ledger against the bank statement by ID; return items unmatched on each side.
2. Same ID, different amount → discrepancy list.
3. Millions of rows: hash join, streaming or sorted merge, memory limits.
4. Fuzzy matching (same amount ± date window when IDs are missing).

**Bugs to catch:** duplicate IDs; currency or minor units; deterministic output order.

---

## 8. Rate Limiter / Account Balancing / Feature Flags (quick reps)
- **Logger rate limiter** (one message per 10 s) → extend to sliding window per key → token bucket.
- **Account rebalancing:** move funds so every account is ≥ $100 (greedy: donors → receivers); minimise the number of transfers.
- **Feature flags:** features restricted by country and A/B eligibility (even user IDs), and combinations of the two.

---

## 9. Your own abuse-control variants (from the earlier plan)
Card-testing velocity detector (`CardTestingDetector.java`, already built with tests), login rate limiter with lockout, fraud-ring union-find, dispute-rate analyser. These match the role, so use them if time is left.

---

## 3-Day Plan

| Day | Morning (≈2 h) | Evening (≈2 h) |
|---|---|---|
| **Day 1** | 30 min in the HackerRank practice env (hr.gs/sampleint-cr): find Plan/Ask/Agent, the Allow dialog, model picker, Run. Rehearse the **opening script** twice. | **#1 Rule Engine**, all 4 parts, timed 50 min, narrating aloud. Review: which AI bugs did you catch? |
| **Day 2** | **#3 Shipping Cost** (40 min) + **#2 Fraud Rules** (40 min). | **#4 Currency** or **#5 Accept-Language** (40 min). Re-do #1 fast (25 min) using parentheses + "which rule fired". |
| **Day 3** | **#6 Store Closing** + **#7 Reconciliation** (35 min each). | Light: reread section 0 + the AI-review checklist from the practice plan; one 30-min mock of #1 with someone interrupting with "why?". Sleep. |

**Each rep:** use Cursor/Copilot sidebar (or the HackerRank practice env) **only through prompts, never solving it yourself first**. That's the skill being tested: steering, reviewing, testing.

---

## Sources
- [interviewdb: Stripe's New AI Programming Exercise Interview](https://www.interviewdb.io/guides/stripe-ai-programming-exercise)
- [1point3acres: Stripe AI-Assisted Coding Round](https://www.1point3acres.com/interview/problems/company/stripe/ai-assisted-coding-round)
- [HackerRank: AI-Assisted Interviews (Plan/Ask/Agent modes)](https://support.hackerrank.com/articles/5821380141-ai-assisted-interviews)
- [GitHub: harry-the-nerd/interview-notes-questions (stripe/shipping-cost-calculator)](https://github.com/harry-the-nerd/interview-notes-questions)
- [GitHub: sumansaurav91/stripe-interview-prep](https://github.com/sumansaurav91/stripe-interview-prep)
- [GitHub: Molakim/stripeCoding](https://github.com/Molakim/stripeCoding) (couldn't be fetched)
- [adonais0: [Interview] Stripe](https://adonais0.github.io/20210603/interview-stripe/)
- [codinginterview.com: Stripe Coding Interview Questions](https://www.codinginterview.com/guide/stripe-interview-questions/)
- [Blind: Stripe AI Programming Exercise Round](https://www.teamblind.com/post/stripe-ai-programming-exercise-round-l2se2-0ipx6xp7) (login-walled)
