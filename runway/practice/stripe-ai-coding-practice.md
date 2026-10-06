# Stripe – Security Engineer, Abuse Control
## AI Programming Exercise: Practice Plan (Java)

**Format:** 60 min on Zoom, HackerRank AI Coding Environment, built-in AI assistant (use it — it's expected). A progressive, multi-part problem. You aren't expected to finish every part.
**What they score:** how you **structure** the solution, how you **direct and review** the AI, how you **test and debug**, and how you **explain decisions**. Careful review beats speed.
**Rules:** Only the HackerRank assistant during the interview. No ChatGPT/Claude/Copilot.

---

## 1. The operating loop (use it on every part)

| Step | What you do | What you say out loud |
|---|---|---|
| **1. Read & restate** (2–3 min) | Restate the part, note inputs/outputs, ask 1–2 clarifying questions (ordering? ties? invalid input? time units?) | "So the input is X, I return Y; I'll assume timestamps are sorted unless you say otherwise." |
| **2. Design first** (2 min) | Write data model + method signatures **yourself** as a short comment block or stubs | "I'll keep a map from cardId to a deque of timestamps — O(1) amortised per event." |
| **3. Prompt narrowly** | Ask the AI for one method or one class at a time, giving it your signatures and constraints | "Implement `isAllowed` using the deque; evict entries older than the window; don't change the signature." |
| **4. Review line by line** | Read every line before running it. Call out what you accept, what you change, and why | "It used `<` where the spec says inclusive, so I'm changing it to `<=`." |
| **5. Test** | Run the given cases, then add your own edge cases (have the AI draft them, you choose which) | "Let me add an empty input, a boundary timestamp, and a duplicate ID." |
| **6. Checkpoint** | Code compiles and passes before moving to the next part. Refactor only when the next part forces it | "Part 2 needs per-merchant config, so I'll pull the threshold out into a rule object first." |

**Golden rule:** you are the tech lead; the AI is a fast junior. Never paste AI output you can't explain.

### Prompt patterns that work well
- **Constrain:** "Using Java 17, no external libraries, keep `processEvent(Event e)` as the entry point."
- **Scope:** "Only write the parser. Don't touch the rule engine yet."
- **Ask for tests separately:** "Give me 6 test cases for `isAllowed` covering window boundaries and multiple keys, as plain `assert`-style checks in `main`."
- **Ask it to critique:** "What edge cases does this implementation miss?" Then *you* decide which ones matter.
- **Debug:** paste the failing output + the method and ask "Why does case 3 produce 2 instead of 3?" Check its explanation against the code before you take the fix.
- **Avoid:** "Solve the whole problem." Big dumps are slow to review and signal low ownership.

---

## 2. AI-output review checklist (Java-specific bugs to hunt)

- [ ] **Window boundaries:** inclusive vs exclusive (`<` vs `<=`), off-by-one when evicting
- [ ] **Money:** `double` used for amounts → use `long` minor units (cents) or `BigDecimal`. Stripe cares about this.
- [ ] **Integer overflow:** sums or timestamps in `int` → use `long`
- [ ] **Map access:** `map.get(k)` can return null → `getOrDefault` / `computeIfAbsent`
- [ ] **equals/hashCode:** custom key classes without them → prefer `record`s
- [ ] **Mutation while iterating:** `ConcurrentModificationException` → use `Iterator.remove()` or `removeIf`
- [ ] **Sorting assumptions:** did it assume input is sorted by time? Does the spec say so?
- [ ] **Ties / determinism:** stable ordering on equal scores or timestamps (tie-break by ID)
- [ ] **Parsing:** trim whitespace, blank lines, malformed rows (skip + count, don't crash), header row
- [ ] **String compare:** `==` instead of `.equals()`; case sensitivity on emails/countries
- [ ] **Complexity:** nested loops over all events → O(n²); per-key indexes instead
- [ ] **Unused/hallucinated APIs:** methods that don't exist in the JDK version
- [ ] **Silent scope creep:** AI changed a signature or rewrote code you'd already checked

---

## 3. Practice problems (abuse-control themed, progressive)

Do each in **45–50 min** with an IDE agent (Cursor, Copilot, or the HackerRank practice env), speaking your reasoning aloud. Then do a 10-minute self-review against the checklist.

### Problem A: Card-testing detector (velocity rules) ⭐ start here
Starter code: `CardTestingDetector.java` (attached).
1. **Part 1:** Input is a list of charge attempts `timestamp,cardFingerprint,ip,amountCents,outcome` (`succeeded|declined`). Flag any card fingerprint with **≥ 5 declines in any 60-second window**. Return the flagged fingerprints, sorted.
2. **Part 2:** Also flag **IPs** that try **≥ 10 distinct cards in 5 minutes**.
3. **Part 3:** Make the rules configurable (key, metric, threshold, window) so new rules can be added without code changes. Support counting `declines` or `distinct cards`.
4. **Part 4:** Turn batch into streaming: `List<Alert> process(Event e)` emits an alert the first time a key trips a rule, with memory bounded by evicting old events.
5. **Stretch:** suppress duplicate alerts for 10 minutes per key and rule; add an allowlist.

*Review focus:* sliding-window eviction, distinct counting inside a window (`Map<String,Integer>` counts, not a `Set`, so eviction works), boundary inclusivity.

### Problem B: Login rate limiter (account takeover / credential stuffing)
1. Token bucket per `userId`: capacity N, refill R/sec. `boolean allow(String user, long tsMillis)`.
2. Add a second limit per IP; a request must pass both. Should a denied request consume a token from the other bucket? (Discuss.)
3. Progressive lockout: after K failed logins, block for an exponentially growing duration (1m, 2m, 4m… capped).
4. Make limits tiered (e.g., new accounts get stricter limits than verified ones).

*Review focus:* refill math with `long`/`double` precision, out-of-order timestamps, both-or-neither consumption.

### Problem C: Risk-scoring rules engine
1. Parse rules like `amount > 50000 AND country != card_country => +30` and score a transaction (JSON-ish map of fields).
2. Add `OR`, parentheses, and `IN (..)`.
3. Decision thresholds: `score ≥ 70 → block`, `≥ 40 → review`, else allow. Return which rules fired (explainability).
4. Add `velocity(card, 1h) > 3`-style aggregate features fed from a history store.

*Review focus:* parser correctness (precedence of AND over OR), typed comparisons (string vs number), clear error messages for bad rules.

### Problem D: Linked-account / fraud-ring detection
1. Given accounts with attributes (email, device ID, card fingerprint, bank account, IP), group accounts that share **any** attribute (union-find).
2. Return clusters of size ≥ 3 with the shared attributes that link them.
3. Ignore "noisy" attributes shared by more than M accounts (e.g., a public Wi-Fi or carrier-NAT IP).
4. Incremental: add accounts one at a time and report when a cluster crosses the threshold.

*Review focus:* union-find with path compression, normalising emails (`+tags`, dots for Gmail, case), handling hub attributes.

### Problem E: Dispute and refund abuse log analyser
1. Parse a CSV of orders/refunds/disputes per customer; compute refund rate and dispute rate per merchant.
2. Flag merchants whose dispute rate is > 0.75% over a rolling 30 days (with a minimum volume).
3. Handle multiple currencies via a given FX table (minor units, rounding rules).
4. Output a report sorted by risk, with ties broken deterministically.

*Review focus:* `BigDecimal` and rounding mode, minimum-sample guardrails, date handling with `Instant`/`LocalDate` in UTC.

---

## 4. Seven-day schedule (fits the 5–7 day booking window)

| Day | Focus | Output |
|---|---|---|
| 1 | Run the HackerRank practice environment (hr.gs/sampleint-cr): learn how the AI panel works, how to run code and tests, and how stdin/stdout is handled | Notes on UI quirks |
| 2 | **Problem A** parts 1–3 in Java with an IDE agent; record yourself thinking aloud | Working code + self-review |
| 3 | Problem A part 4 + **Problem B** | Practise prompting for one method at a time |
| 4 | **Problem C**: deliberately let the AI write the parser, then hunt for its bugs | List of bugs you caught |
| 5 | **Problem D** under a strict 50-min timer | Timed run |
| 6 | **Problem E** + full mock: a friend (or the recording) plays interviewer and interrupts with "why?" | Polish your narration |
| 7 | Light: reread checklist, Java cheat sheet, rest | — |

---

## 5. Java cheat sheet (what you'll actually type or review)

```java
// Immutable event types — free equals/hashCode/toString
record Event(long ts, String card, String ip, long amountCents, String outcome) {}

// Per-key sliding window
Map<String, Deque<Long>> windows = new HashMap<>();
Deque<Long> dq = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
dq.addLast(ts);
while (!dq.isEmpty() && dq.peekFirst() <= ts - windowMs) dq.pollFirst(); // check inclusivity!

// Distinct count within a window: counts map, so eviction can decrement
Map<String, Integer> cardCounts = new HashMap<>();
cardCounts.merge(card, 1, Integer::sum);
cardCounts.computeIfPresent(oldCard, (k, v) -> v == 1 ? null : v - 1); // null removes

// Parsing
for (String line : input.split("\\R")) {
    if (line.isBlank()) continue;
    String[] p = line.trim().split("\\s*,\\s*");
    try { long ts = Long.parseLong(p[0]); } catch (NumberFormatException e) { malformed++; continue; }
}

// Deterministic sort: score desc, then id asc
list.sort(Comparator.comparingInt(Alert::score).reversed().thenComparing(Alert::key));

// Money
BigDecimal usd = new BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_EVEN);

// Quick tests with no JUnit
static void check(String name, Object got, Object want) {
    System.out.println((Objects.equals(got, want) ? "PASS " : "FAIL ") + name + " got=" + got + " want=" + want);
}
```

Also worth having fluent: `TreeMap` (`floorKey`, `headMap`) for time-ordered lookups, `PriorityQueue` for top-K, `Collectors.groupingBy`, `String.format`, `Instant.ofEpochSecond`.

---

## 6. Talking points that show security judgment (sprinkle in, briefly)

- **False positives cost money:** "In abuse control a false positive blocks a legitimate merchant's revenue, so I'd make thresholds configurable and start in log-only mode."
- **Attacker adaptation:** "Fixed thresholds get probed; attackers will sit at 4 declines. That's where distinct-IP and cross-key signals come in."
- **Evasion:** "Fingerprints rather than raw PANs; normalise emails; watch for IP rotation and low-and-slow patterns."
- **Scale:** "In production this state would live in Redis or a stream processor, sharded by key. Here I'm using in-memory maps."
- **Explainability:** "Return which rule fired so ops can review and merchants can appeal."

---

## 7. Day-of checklist

- [ ] Tell the recruiter: **Java** (Java 17+ features such as records, `var`, and switch expressions)
- [ ] Quiet room, second monitor if allowed, Zoom tested, HackerRank opened in Chrome
- [ ] First 3 min: restate the problem, ask clarifying questions, write stubs
- [ ] Narrate every accept, reject, or change of AI output
- [ ] Get Part 1 fully passing before moving on; partial but correct beats complete but broken
- [ ] Last 5 min: summarise what works, what you'd test next, and how you'd productionise it
