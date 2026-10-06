# Scaling the Card-Testing Detector: From Interview Code to Millions of Events/sec

This explains how the Java 8 solution in `CardTestingDetector.java` evolves when traffic grows from a test file to **millions of events per second**, and links every production concept back to a line of the ground-level code.

---

## 0\. Is the sliding window static or dynamic?

| What you measure | Fixed or variable? | In our code |
| :---- | :---- | :---- |
| **Time span** | Fixed: always the last W seconds | `limit = now - W` |
| **Number of elements** | Variable: the deque grows and shrinks | 1 event at one moment, 4 at another |

In interview terms this is a **dynamic (variable-size), time-based sliding window**. One new event can evict **zero, one or many** old events, which is why eviction is a `while` loop.

declines at 1, 5, 12, 14, 15, 24   (W \= 10\)

t=1  \[1\]            size 1

t=5  \[1,5\]          size 2   grew

t=12 \[5,12\]         size 2   \+1 / \-1

t=14 \[5,12,14\]      size 3   grew

t=15 \[5,12,14,15\]   size 4   grew

t=24 \[14,24\]        size 2   \+1 / \-3 (shrank)

**Sliding vs tumbling windows:** a tumbling window uses fixed clock buckets (10:00–10:01, 10:01–10:02). It can **miss attacks that straddle a boundary**: 3 declines at 10:00:58 plus 3 at 10:01:02 count as 3 \+ 3 and never reach 5\. A sliding window sees 6 within 4 seconds.

---

## 1\. Clarify and estimate

Ask the interviewer:

- What is the peak rate? Assume **1M events/sec**.  
- What latency is needed? **Inline** in the payment authorisation (\< \~10 ms) or **async** alerting (seconds)?  
- How exact must it be? Is ±1% / ±1 s acceptable?  
- How many active keys? Assume \~50M cards and \~10M IPs per window.

Rough sizing of the current code at that rate:

| Rule | Events inside the window | Memory with the current code |
| :---- | :---- | :---- |
| Card, 60 s | 1M × 60 \= **60M** timestamps | \~24 B each (boxed `Long` \+ deque slot) ≈ **1.5 GB**, plus map overhead for 50M keys ≈ **5+ GB** |
| IP, 300 s | 1M × 300 \= **300M** `Event` objects | \~100 B each ≈ **30 GB** |

So one JVM won't hold it, garbage-collection pauses would stall payments, and one thread can't keep up.

---

## 2\. Where the current code breaks

| Ground-level code | Problem at scale |
| :---- | :---- |
| `Deque<Long>` / `Deque<Event>` per key | Memory grows with **traffic**, not keys |
| `HashMap` in one thread | Not thread-safe; one CPU core is the ceiling |
| Everything in memory | A restart loses all windows, so attackers get a free pass |
| `Collections.sort(events)` | Real streams never end and arrive **out of order** |
| `TreeSet flagged` | Alerts are needed **immediately, per event**, not at the end of a batch |

---

## 3\. Evolve in stages

### Stage 1: harden the single node

- **Store less per event:** the card window needs only `ts`; the IP window needs `(ts, cardId)` as primitives, not whole `Event` objects. Use primitive collections (fastutil / Agrona) to avoid boxing.  
- **Remove empty keys** after eviction: `if (dq.isEmpty()) windows.remove(key);`  
- **Partition by key across threads:** `thread = hash(card) % N`. Each thread owns its keys, so **no locks** are needed.  
    
  > This is the most important idea in the whole design. Kafka partitions do the same thing at cluster scale.

### Stage 2: bound the memory, with buckets instead of timestamps

Replace the deque with a fixed ring of count buckets per key (e.g. 60 one-second buckets):

card\_A:  \[0\]\[2\]\[0\]\[1\]\[0\] ... \[3\]     60 ints, always

ADD   \-\> bucket\[ts % 60\]++   (reset first if it holds an old second)

EVICT \-\> stale buckets are ignored or overwritten

CHECK \-\> sum of fresh buckets \>= N

// Java 8 bucketed window (one per key)

final class BucketedWindow {

    private final int\[\] counts;

    private final long\[\] bucketId;      // which second each slot currently holds

    private final long bucketSeconds;

    BucketedWindow(int buckets, long bucketSeconds) {

        this.counts \= new int\[buckets\];

        this.bucketId \= new long\[buckets\];

        Arrays.fill(this.bucketId, \-1);

        this.bucketSeconds \= bucketSeconds;

    }

    void add(long ts) {

        long b \= ts / bucketSeconds;

        int i \= (int) (b % counts.length);

        if (bucketId\[i\] \!= b) { bucketId\[i\] \= b; counts\[i\] \= 0; }   // reuse stale slot

        counts\[i\]++;

    }

    int sum(long now) {

        long cur \= now / bucketSeconds;

        int s \= 0;

        for (int i \= 0; i \< counts.length; i++) {

            if (bucketId\[i\] \>= 0 && cur \- bucketId\[i\] \< counts.length) s \+= counts\[i\];

        }

        return s;

    }

}

- **Memory now grows with active keys, not traffic:** 50M keys × 60 × 4 B ≈ 12 GB, which shards easily. Store only non-empty buckets to cut it further.  
- **Distinct cards per IP:** a small exact set per bucket, switching to **HyperLogLog** (see section 6\) for large counts.  
- **Trade-off:** the window edge is precise to ±1 bucket. Fine for fraud thresholds; say it out loud.

### Stage 3: distribute, with stream processing

 Payments API ──► Kafka "charge\_attempts"

                     │

         ┌───────────┴────────────┐

   repartition by card       repartition by IP

         │                        │

   Flink job: card rule      Flink job: IP rule        ← keyed state (RocksDB),

   (keyed by card)           (keyed by ip)               checkpointed to S3

         └───────────┬────────────┘

                     ▼

            Kafka "risk\_alerts"

                     │

     ┌───────────────┼──────────────────┐

 Enforcement store   Case mgmt / ops     Merchant alerts

 (Redis blocklist,   dashboard

  TTL 30–60 min)

- **Partition by key:** all events for `card_A` land on one partition and one worker. That's `Map<card, window>` spread across a cluster.  
- **Each rule partitions by its own key** (card vs IP), mirroring the two maps in the code.  
- **Keyed state in RocksDB \+ checkpoints:** windows survive restarts.  
- **Event time \+ watermarks** replace `Collections.sort`: wait a bounded time for late events, then move on.

### Stage 4: split the hot path from the cold path

| Path | Purpose | Tech | Latency | Accuracy |
| :---- | :---- | :---- | :---- | :---- |
| **Hot (inline)** | Decide *this* charge now | Redis cluster: `INCR card:A:{second}` \+ `EXPIRE 120`, sum the last 60 keys (or a sorted set per key) | \< 5 ms | Approximate |
| **Cold (streaming)** | Rich features, distinct counts, linked accounts | Flink \+ HyperLogLog, feeding scores back to the hot store | Seconds | Accurate |

Payments must never wait on a heavy computation, so the authorisation path reads **precomputed** counters only.

---

## 4\. Cross-cutting concerns

| Concern | Design decision |
| :---- | :---- |
| **Hot keys** (botnet IP, carrier NAT IP shared by many real users, huge merchant) | Split the key across sub-keys (`ip#0..ip#7`) and sum them when reading; pre-aggregate locally; give known shared IPs an allowlist or higher thresholds |
| **Duplicates / retries** | Deduplicate by `charge_id`; idempotent writes, so a retry can't increment twice |
| **Late / out-of-order events** | Event time, watermark, allowed lateness; late events update counts but don't re-fire old alerts |
| **Failure mode** | If the counter store is down: **fail open** for normal payments, **fail closed** for high-risk actions |
| **Multi-region** | Count locally in each region, merge asynchronously; accept short-lived under-counting |
| **Rules as data** | Key, metric, window and threshold in config, hot-reloaded; new rules start in **shadow mode** (log only) |
| **Alert de-duplication** | One alert per key per rule per cooldown (e.g. 10 min) |
| **Response** | Flag means friction (3-D Secure, CAPTCHA), then a temporary block, then a merchant alert. Not an instant permanent block |
| **Observability** | Consumer lag, alerts per rule per minute, false-positive rate (disputes/appeals), state size per key |
| **Privacy / PCI** | Key on **card fingerprints**, never raw card numbers; set retention limits on state |

---

## 5\. From the interview code to production

| In `CardTestingDetector.java` | In production |
| :---- | :---- |
| `Map<String, Deque<Long>> windows` | Kafka partition by key \+ Flink keyed state / Redis keys |
| `dq.addLast(ts)` | Increment the current time bucket |
| `while (peekFirst() < limit) pollFirst()` | Buckets expire through a ring-buffer overwrite or Redis `EXPIRE` |
| `dq.size() >= N` | Sum the fresh buckets ≥ threshold |
| `Map<card, count>` for distinct cards | Per-bucket set, then HyperLogLog, merged on read |
| `Collections.sort(events)` | Event-time processing with watermarks |
| `TreeSet<String> flagged` | Alert topic \+ cooldown de-duplication |
| Constants `CARD_WINDOW = 60` | Rule config service, versioned, shadow mode first |
| Single thread | One owner per key (partition), with no shared locks |

---

## 6\. HyperLogLog: distinct counts in fixed memory

**What it is:** a way to estimate **how many different things** you've seen (distinct cards, unique IPs) in **1–12 KB of fixed memory**, with about **±1–2% error**, whether you've seen 100 items or 1 billion.

**Why we need it:** our exact `Map<card, count>` is fine for a 10-card threshold. But "distinct cards per merchant per day" for a merchant seeing 5M cards needs 200+ MB per merchant as an exact set. HyperLogLog does it in about 12 KB.

### Intuition: coin flips

If the longest run of heads anyone flipped today was 10, about 2¹⁰ ≈ 1,000 people flipped. You only had to remember **the longest run**, not every person.

HLL does the same with hashes:

1. **Hash** each card ID into random-looking bits.  
2. Count **leading zeros**: k leading zeros suggests about 2ᵏ distinct items.  
3. Remember only the **maximum** seen.  
4. Duplicates don't matter: the same card gives the same hash, so the maximum doesn't change. That's why it counts *distinct* items.

### Accuracy: many buckets

- The first bits of the hash pick one of **m registers** (e.g. 14 bits gives 16,384).  
- The remaining bits give the leading-zero count; each register stores only its maximum (6 bits).  
- The estimate is the harmonic mean of all registers, times a correction constant.

hash(card) \= \[ 01101010101101 | 0001 0111 ... \]

               └ register idx ┘  └ leading zeros → 3 ┘

               register\[6829\] \= max(old, 3 \+ 1\)

Error ≈ **1.04 / √m**:

| Registers (m) | Memory | Typical error |
| :---- | :---- | :---- |
| 1,024 | \~1 KB | \~3.2% |
| 4,096 | \~3 KB | \~1.6% |
| 16,384 | \~12 KB | \~0.8% |

### The superpower: merging

`HLL(A) ∪ HLL(B)` \= register-by-register **max**, which gives exactly the HLL of all items together. That makes it ideal for sliding windows (merge time buckets) and distributed systems (merge workers and regions).

### The catch: no delete

HLL stores only maximums, so you **can't remove** an item. In a sliding window you **drop whole time buckets** instead of evicting items:

IP 9.9.9.9, 5-min window, 10 s buckets:

 \[HLL t0\]\[HLL t1\] ... \[HLL t29\]   ← oldest bucket gets reset and reused

 distinct ≈ count( merge(fresh buckets) )

### Exact vs HyperLogLog

|  | Exact count map (our code) | HyperLogLog |
| :---- | :---- | :---- |
| Memory | Grows with distinct items | **Fixed** (1–12 KB) |
| Answer | Exact | ±1–2% |
| Remove an item | ✅ (−1 on evict) | ❌ (drop whole buckets) |
| Merge across machines | Expensive | **Cheap** (register max) |
| Best for | Small thresholds (e.g. 10\) | Huge counts, dashboards, long windows |

**Hybrid:** keep an exact small set while the count is small and switch to HLL when it grows (Redis does this internally).

**Where you'll see it:** Redis `PFADD` / `PFCOUNT`; BigQuery, Snowflake and Trino `APPROX_COUNT_DISTINCT`; Apache DataSketches in Flink/Spark.

---

## 7\. The 60-second architect answer

> "At a million events a second, exact per-event deques cost tens of gigabytes and a single thread can't keep up. I keep the same add, evict, check logic but change three things. First, **partition by key** (Kafka, then Flink keyed state) so each key has a single owner and there are no locks. Second, replace stored timestamps with **fixed time buckets per key**, plus **HyperLogLog** for distinct counts, so memory grows with active keys, not traffic. Third, split a **hot path**, where the authorisation reads precomputed Redis counters in milliseconds, from a **cold path** that computes richer features asynchronously. Around that I handle hot keys by splitting them, duplicates with idempotency keys, late events with watermarks, and false positives by rolling out rules in shadow mode, with friction before blocking."

**HyperLogLog in one line:**

> "Hash each item, keep only the maximum run of leading zeros per register, and estimate cardinality from those: about 1% error in around 12 KB regardless of volume. It can't delete, so for sliding windows I keep one HLL per time bucket and merge the fresh ones."