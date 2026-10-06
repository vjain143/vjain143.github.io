# Data Structures & Algorithms — Java Quick Reference for Progressive Problems

A working reference for timed, multi-part coding exercises: problems where requirements arrive in stages (parse → apply a rule → add rules → add state over time → handle messy input). Every snippet compiles and runs on **Java 21**.

> **Rule:** choose the structure from *the question the code must answer*. Say the choice and its Big-O out loud, then tell the AI assistant which structure to use. Don't let it choose for you.

---

## Contents

1. [Signal → structure](#1-signal--structure)
2. [HashMap — lookup, grouping, totals](#2-hashmap--lookup-grouping-totals)
3. [HashSet — duplicates and idempotency](#3-hashset--duplicates-and-idempotency)
4. [TreeMap — ranges, tiers, brackets](#4-treemap--ranges-tiers-brackets)
5. [ArrayDeque — sliding windows and queues](#5-arraydeque--sliding-windows-and-queues)
6. [PriorityQueue — top-K and scheduling](#6-priorityqueue--top-k-and-scheduling)
7. [LinkedHashMap — LRU cache and stable order](#7-linkedhashmap--lru-cache-and-stable-order)
8. [Union-Find — linked entities](#8-union-find--linked-entities)
9. [Graph + BFS — reachability](#9-graph--bfs--reachability)
10. [Token bucket — rate limiting](#10-token-bucket--rate-limiting)
11. [Sort + sweep — merging intervals](#11-sort--sweep--merging-intervals)
12. [Prefix sums — fast range totals](#12-prefix-sums--fast-range-totals)
13. [Binary search — lower bound](#13-binary-search--lower-bound)
14. [Modelling defaults](#14-modelling-defaults)
15. [How to talk about your choice](#15-how-to-talk-about-your-choice)
16. [Complexity cheat sheet](#16-complexity-cheat-sheet)

---

## 1. Signal → structure

| If the problem says… | Reach for | Key operations | Cost |
|---|---|---|---|
| "look up by id / country / product" | `HashMap` | `get`, `getOrDefault`, `computeIfAbsent` | O(1) |
| "count / total per key" | `HashMap` + `merge` | `merge(k, v, Long::sum)` | O(1) |
| "group items by key" | `HashMap<K, List<V>>` | `computeIfAbsent(k, x -> new ArrayList<>())` | O(1) |
| "seen before? duplicate? idempotency key" | `HashSet` | `add` returns `false` if present | O(1) |
| "which tier / range / bracket does X fall in" | `TreeMap` | `floorEntry`, `ceilingEntry`, `subMap` | O(log n) |
| "keep sorted, process in order" | `ArrayList` + `sort` | `sort(Comparator)` | O(n log n) |
| "in the last T seconds / N per window" | `ArrayDeque` per key | `addLast`, `peekFirst`, `pollFirst` | amortised O(1) |
| "top K / highest risk / earliest expiry" | `PriorityQueue` | `offer`, `poll` | O(n log K) |
| "most recent / evict oldest / cache" | `LinkedHashMap` (access order) | `removeEldestEntry` | O(1) |
| "deterministic output order" | `LinkedHashMap` or `TreeMap` | — | — |
| "entities linked by a shared attribute" | Union-Find | `find`, `union` | ~O(1) |
| "what can X reach / degrees of separation" | Graph + BFS | adjacency map + queue | O(V + E) |
| "N requests per second, allow bursts" | Token bucket | refill on read | O(1) |
| "overlapping time ranges" | Sort + sweep | sort by start | O(n log n) |
| "total between two points, many queries" | Prefix-sum array | `prefix[j] - prefix[i]` | O(1) per query |
| "first value ≥ X in sorted data" | Binary search | lower bound | O(log n) |

---

## 2. HashMap — lookup, grouping, totals

**Use when** the question is "given a key, what's the value?"

```java
// Totals per key
Map<String, Long> totals = new HashMap<>();
totals.merge(merchantId, amount, Long::sum);

// Group items by key
Map<String, List<String>> byMerchant = new HashMap<>();
byMerchant.computeIfAbsent(merchantId, k -> new ArrayList<>()).add(txId);

// Nested config: country -> product -> value
Map<String, Map<String, Long>> config = new HashMap<>();
long cost = config.getOrDefault(country, Map.of()).getOrDefault(product, 0L);
```

**Pitfalls**
- `getOrDefault` hides missing config. Decide whether an unknown key is an error or a default, and say which.
- Iteration order is not guaranteed. Use `LinkedHashMap` or `TreeMap` when the output order is tested.
- Mutating a map while iterating over it throws `ConcurrentModificationException`.

---

## 3. HashSet — duplicates and idempotency

**Use when** the question is "have I seen this before?"

```java
Set<String> seen = new HashSet<>();
if (!seen.add(idempotencyKey)) {
    // duplicate — skip, or return the original result
}
```

**Pitfalls**
- Records get `equals` and `hashCode` for free. Plain classes used as keys need both overridden.
- The set grows without bound. Ask whether keys expire, and if so pair it with a time-ordered structure.

---

## 4. TreeMap — ranges, tiers, brackets

**Use when** the question is "which range contains X?"

```java
TreeMap<Long, String> tiersByMin = new TreeMap<>();
tiersByMin.put(0L, "tier1");
tiersByMin.put(2L, "tier2");
tiersByMin.put(10L, "tier3");

Map.Entry<Long, String> e = tiersByMin.floorEntry(5L);   // -> tier2
if (e == null) { /* X is below the smallest key */ }
```

| Method | Returns |
|---|---|
| `floorEntry(x)` | greatest key ≤ x |
| `ceilingEntry(x)` | smallest key ≥ x |
| `lowerEntry(x)` / `higherEntry(x)` | strictly < x / strictly > x |
| `subMap(a, true, b, false)` | the view `[a, b)` |
| `headMap(x)` / `tailMap(x)` | keys < x / keys ≥ x |

**Pitfalls**
- `floorEntry` returns `null` below the first key. Always null-check it.
- Duplicate keys overwrite each other. Validate config first.
- For 3–5 ranges, a sorted list with a linear scan is fine and simpler. Say so.

---

## 5. ArrayDeque — sliding windows and queues

**Use when** the question is "how many events in the last T?"

```java
Map<String, ArrayDeque<Long>> windows = new HashMap<>();

boolean overLimit(String key, long ts, long windowMs, int limit) {
    ArrayDeque<Long> q = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
    while (!q.isEmpty() && q.peekFirst() <= ts - windowMs) q.pollFirst();   // evict old
    q.addLast(ts);
    return q.size() > limit;
}
```

**Clarify before coding**
- Do events arrive in timestamp order? If not, sort first or use a `TreeMap<Long, Integer>`.
- Is the window boundary inclusive or exclusive? (`<=` vs `<` in the eviction line.)
- Does a blocked event still count towards the window?

**Pitfalls**
- Use `ArrayDeque`, not `Stack` or `LinkedList`. It's faster and the idiomatic choice.
- `ArrayDeque` rejects `null` elements.

---

## 6. PriorityQueue — top-K and scheduling

**Use when** the question is "the K largest" or "what's next by time or priority?"

```java
// Top-K by value: keep a min-heap of size K
PriorityQueue<Map.Entry<String, Long>> pq =
        new PriorityQueue<>(Map.Entry.comparingByValue());
for (var e : totals.entrySet()) {
    pq.offer(e);
    if (pq.size() > k) pq.poll();         // drop the smallest
}
List<String> topK = new ArrayList<>();
while (!pq.isEmpty()) topK.add(pq.poll().getKey());
Collections.reverse(topK);                 // highest first
```

**Pitfalls**
- Java's `PriorityQueue` is a **min**-heap. For a max-heap use `Comparator.reverseOrder()`.
- Iterating over a `PriorityQueue` is **not** in sorted order. Only `poll()` is.
- Ties: add a secondary comparator (e.g. `.thenComparing(Map.Entry::getKey)`) for deterministic output.

---

## 7. LinkedHashMap — LRU cache and stable order

**Use when** the question is "keep the most recent N" or "preserve insertion order."

```java
class Lru<K, V> extends LinkedHashMap<K, V> {
    private final int cap;
    Lru(int cap) { super(16, 0.75f, true); this.cap = cap; }   // true = access order
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) { return size() > cap; }
}
```

**Pitfalls**
- The third constructor argument `true` is what makes it an LRU. Without it you get insertion order (FIFO).
- In access-order mode, `get` changes the iteration order.

---

## 8. Union-Find — linked entities

**Use when** the question is "which accounts are connected through a shared card, device or IP?"

```java
Map<String, String> parent = new HashMap<>();

String find(String x) {
    parent.putIfAbsent(x, x);
    String p = parent.get(x);
    if (!p.equals(x)) { p = find(p); parent.put(x, p); }   // path compression
    return p;
}

void union(String a, String b) { parent.put(find(a), find(b)); }

// Link each account to the attributes it uses:
// union("acct1", "card9"); union("acct2", "card9");
// find("acct1").equals(find("acct2")) -> true
```

**Pitfalls**
- Recursion depth can be large before compression on adversarial input. An iterative `find` is safer at scale.
- Union-Find answers "are these connected?", not "what's the path?". Use BFS for paths.

---

## 9. Graph + BFS — reachability

**Use when** the question is "what can X reach?" or "how many hops between A and B?"

```java
Set<String> reachable(Map<String, List<String>> graph, String start) {
    Set<String> seen = new HashSet<>(List.of(start));
    ArrayDeque<String> q = new ArrayDeque<>(List.of(start));
    while (!q.isEmpty()) {
        String node = q.pollFirst();
        for (String next : graph.getOrDefault(node, List.of()))
            if (seen.add(next)) q.addLast(next);
    }
    return seen;
}
```

**Pitfalls**
- Mark nodes as seen when you **enqueue** them, not when you dequeue, to avoid duplicates in the queue.
- Directed or undirected? For undirected graphs, add both edges.

---

## 10. Token bucket — rate limiting

**Use when** the question is "allow N per second, with bursts."

```java
class TokenBucket {
    private final double capacity, refillPerMs;
    private double tokens;
    private long last;

    TokenBucket(double capacity, double refillPerMs, long now) {
        this.capacity = capacity; this.refillPerMs = refillPerMs;
        this.tokens = capacity; this.last = now;
    }

    boolean allow(long now) {
        tokens = Math.min(capacity, tokens + (now - last) * refillPerMs);
        last = now;
        if (tokens >= 1) { tokens -= 1; return true; }
        return false;
    }
}
```

**Trade-off to say out loud:** a token bucket uses O(1) memory per key and allows bursts. A sliding window ([§5](#5-arraydeque--sliding-windows-and-queues)) is exact but stores every timestamp. Pick based on whether bursts are acceptable.

---

## 11. Sort + sweep — merging intervals

**Use when** the question is "merge overlapping ranges" or "find overlaps."

```java
record Interval(long start, long end) {}

List<Interval> merge(List<Interval> input) {
    List<Interval> sorted = new ArrayList<>(input);
    sorted.sort(Comparator.comparingLong(Interval::start));
    List<Interval> out = new ArrayList<>();
    for (Interval cur : sorted) {
        if (!out.isEmpty() && cur.start() <= out.get(out.size() - 1).end()) {
            Interval last = out.remove(out.size() - 1);
            out.add(new Interval(last.start(), Math.max(last.end(), cur.end())));
        } else {
            out.add(cur);
        }
    }
    return out;
}
```

**Pitfalls**
- `<=` merges touching intervals like `[1,3]` and `[3,5]`. For half-open `[start, end)` ranges, use `<` instead. Ask which applies.
- Copy before sorting so you don't mutate the caller's list.

---

## 12. Prefix sums — fast range totals

**Use when** you need many "sum between i and j" queries over fixed data.

```java
long[] prefix = new long[amounts.length + 1];
for (int i = 0; i < amounts.length; i++) prefix[i + 1] = prefix[i] + amounts[i];

long sum = prefix[j + 1] - prefix[i];   // sum of amounts[i..j] inclusive
```

**Pitfalls**
- The array has length n + 1. The off-by-one is the whole trick.
- Only works for static data. For updates use a Fenwick tree, or just recompute if updates are rare.

---

## 13. Binary search — lower bound

**Use when** the question is "first position where value ≥ X" in sorted data.

```java
int lowerBound(long[] a, long x) {          // first index with a[i] >= x; a.length if none
    int lo = 0, hi = a.length;
    while (lo < hi) {
        int mid = (lo + hi) >>> 1;          // avoids int overflow
        if (a[mid] < x) lo = mid + 1; else hi = mid;
    }
    return lo;
}
```

**Pitfalls**
- `Arrays.binarySearch` returns *any* matching index when there are duplicates, and a negative insertion point when the value is missing. Write your own if you need "first."
- `(lo + hi) / 2` can overflow. Use `>>> 1`.

---

## 14. Modelling defaults

| Concern | Default | Why |
|---|---|---|
| Immutable data | `record` | Free `equals`/`hashCode`/`toString`, safe as map keys |
| Closed set of values | `enum` | Exhaustive `switch`, no stringly-typed bugs |
| Money | `long` in minor units, or `BigDecimal` with explicit `RoundingMode` | `double` loses cents |
| Arithmetic on money | `Math.addExact`, `Math.multiplyExact` | Throws on overflow instead of wrapping silently |
| Time | `long` epoch millis or `Instant` | Ask about timezones before using `LocalDateTime` |
| CSV parsing | `line.split(",", -1)` | Keeps trailing empty fields |
| Number parsing | `Long.parseLong` inside try/catch | Decide whether to skip or fail on bad input |
| Returning collections | `List.copyOf`, `Map.copyOf` | Callers can't mutate internal state |
| Validation | In the constructor, once | Fail fast; the rest of the code can trust the data |

---

## 15. How to talk about your choice

A good structure decision, said out loud, has three parts:

1. **The question:** "The code needs to answer 'which range contains X'."
2. **The choice and cost:** "A TreeMap gives me O(log n) lookup with `floorEntry`."
3. **The simpler alternative and when to switch:** "With three tiers, a sorted list and a linear scan is fine, so I'll start there and switch if the tier count grows."

Naming the simpler option, and the condition under which you'd upgrade, signals more judgement than reaching for the cleverest structure.

**When working with an AI assistant**
- Decide the structure yourself, then prompt with it: *"Use a `Map<String, ArrayDeque<Long>>` keyed by card ID for the sliding window. No other classes."*
- Reject over-engineering out loud: a Strategy pattern for two cases, factories for one implementation, interfaces nobody else implements.
- Check every diff for the pitfalls listed in each section above.

---

## 16. Complexity cheat sheet

| Structure | Get / contains | Insert | Remove | Ordered iteration |
|---|---|---|---|---|
| `ArrayList` | O(1) by index, O(n) by value | O(1) amortised at end | O(n) | Insertion order |
| `HashMap` / `HashSet` | O(1) | O(1) | O(1) | None |
| `LinkedHashMap` | O(1) | O(1) | O(1) | Insertion or access order |
| `TreeMap` / `TreeSet` | O(log n) | O(log n) | O(log n) | Sorted |
| `ArrayDeque` | O(1) at ends | O(1) at ends | O(1) at ends | FIFO / LIFO |
| `PriorityQueue` | O(1) peek | O(log n) | O(log n) poll | Only via `poll` |
| Union-Find | ~O(1) find | ~O(1) union | — | — |

| Algorithm | Time | Space |
|---|---|---|
| Sort (`List.sort`) | O(n log n) | O(n) |
| Binary search | O(log n) | O(1) |
| BFS / DFS | O(V + E) | O(V) |
| Top-K with heap | O(n log K) | O(K) |
| Interval merge | O(n log n) | O(n) |
| Prefix-sum build / query | O(n) / O(1) | O(n) |
