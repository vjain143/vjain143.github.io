# AI-Assisted Coding Interview Playbook (Staff Engineer Level)

Context: `CardTestingDetector.java` — implement sliding-window fraud detection
(`flagCards`, `flagIps`, `parse`) with an AI assistant, reviewing every line.

Graders aren't just checking if tests pass — they're watching **how you direct
the AI**: do you clarify requirements, catch its mistakes, reason about edge
cases and scale, or just paste the TODO and accept whatever comes back.

---

## 1. Restate the spec before writing code

> Before implementing, restate the exact semantics of `flagCards` and
> `flagIps`: window inclusivity, what counts as a "distinct card," how
> ties/unsorted timestamps are handled, and how malformed/blank/header lines
> should be skipped in `parse`. List any ambiguities you see before coding.

Forces the AI to surface the inclusive-boundary rule (`ts - first <= 60`),
case-insensitive `outcome` matching, and unsorted-input handling — things the
hidden tests already probe. Catching these **before** code is the staff
signal.

## 2. Ask for the algorithm, not the code, first

> Propose a sliding-window algorithm for each function with its time
> complexity. I want O(n) or O(n log n), not O(n²). Explain the data
> structure choice before writing Java.

Demonstrate systems thinking — e.g., sort-by-timestamp + two-pointer/deque
for `flagCards`, and a per-IP ordered structure with a shrinking window +
distinct-card count for `flagIps`.

## 3. Implement incrementally, one function at a time

> Implement `parse` only. Show me the code and explain how it handles the
> `garbage line` and header row in the test data.

Then separately for `flagCards`, then `flagIps`. Don't let it dump all three
at once — reviewing in chunks is what "reviewing every line" is testing.

## 4. Make it run and interpret failures yourself

> Run `javac` and `java` on this file and show the test output.

If something fails, don't say "fix it" — say:

> Test X expects Y, got Z — walk through the trace for that input and tell
> me which line is wrong before patching it.

Shows you debug, not just regenerate.

## 5. Adversarial review pass

> Review your own implementation for: off-by-one on window boundaries,
> mutating vs. copying the input list, behavior on duplicate timestamps, and
> whether `flagCards`/`flagIps` preserve a deterministic output order. Fix
> anything you find.

## 6. Push into staff territory (parts 3–4 from the practice plan)

> Now refactor so the decline-window/threshold and IP-window/threshold
> aren't hardcoded constants but injectable config (e.g., a `Rules`
> record), without breaking the existing tests.

> Sketch how this would change if events arrive as a live stream instead of
> a batch list — what state would you need to keep per card/IP, and how
> would you evict it?

This is what differentiates "senior got it working" from "staff engineer" —
discussing streaming state, memory bounds, and config extensibility out
loud.

## 7. Close with trade-offs, unprompted

> What would break this in production at Stripe's actual scale, and what's
> the minimal change to fix it?

---

## Quick checklist to hit while narrating

- [ ] Clarified window inclusivity and case-insensitivity before coding
- [ ] Discussed algorithm/complexity before implementation
- [ ] Reviewed AI's code chunk-by-chunk, not all at once
- [ ] Ran tests myself and diagnosed failures before asking for fixes
- [ ] Did an explicit adversarial/edge-case review pass
- [ ] Extended to configurable rules (part 3)
- [ ] Discussed streaming/production version (part 4)
- [ ] Named a concrete production failure mode + fix