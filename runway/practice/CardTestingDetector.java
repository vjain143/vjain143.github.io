package com.vishvapi;

import java.util.*;

/**
 * Stripe practice - Problem A: Card-testing detector. STARTER.
 * Implement the TODOs with an AI assistant, reviewing every line. Tests in main() must pass.
 * Then continue with parts 3-4 from the practice plan (configurable rules, streaming).
 * Run: javac CardTestingDetector.java && java CardTestingDetector
 */
public class CardTestingDetector {

    record Event(long ts, String card, String ip, long amountCents, String outcome) {}

    static final long DECLINE_WINDOW = 60;      // seconds
    static final int DECLINE_THRESHOLD = 5;
    static final long IP_WINDOW = 300;          // seconds
    static final int IP_DISTINCT_THRESHOLD = 10;

    /** Parses "ts,card,ip,amountCents,outcome" lines; skips blank, header and malformed rows. */
    static List<Event> parse(String input) {
        List<Event> events = new ArrayList<>();
        for (String line : input.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split(",", -1);
            if (fields.length != 5) {
                continue;
            }
            boolean missingField = false;
            for (int i = 0; i < fields.length; i++) {
                fields[i] = fields[i].strip();
                missingField |= fields[i].isEmpty();
            }
            if (missingField || fields[0].equalsIgnoreCase("ts")) {
                continue;
            }
            try {
                long timestamp = Long.parseLong(fields[0]);
                long amountCents = Long.parseLong(fields[3]);
                events.add(new Event(timestamp, fields[1], fields[2], amountCents, fields[4]));
            }
            catch (NumberFormatException ignored) {
                // Skip rows with invalid or out-of-range numeric values.
            }
        }
        return events;
    }

    /** Part 1: cards with >= 5 declines within any 60s window (inclusive: ts - first <= 60). */
    static List<String> flagCards(List<Event> events) {
        // TODO
        return new ArrayList<>();
    }

    /** Part 2: IPs that attempt >= 10 distinct cards within any 300s window. */
    static List<String> flagIps(List<Event> events) {
        // TODO
        return new ArrayList<>();
    }

    // ---------------- tests ----------------
    static int failures = 0;
    static void check(String name, Object got, Object want) {
        boolean ok = Objects.equals(got, want);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + "  got=" + got + "  want=" + want);
    }

    public static void main(String[] args) {
        // Part 1
        String p1 = """
            ts,card,ip,amount,outcome
            0,card_A,1.1.1.1,100,declined
            10,card_A,1.1.1.1,100,declined
            20,card_A,1.1.1.1,100,declined
            30,card_A,1.1.1.1,100,declined
            40,card_A,1.1.1.1,100,declined
            0,card_B,2.2.2.2,100,declined
            30,card_B,2.2.2.2,100,declined
            61,card_B,2.2.2.2,100,declined
            90,card_B,2.2.2.2,100,declined
            121,card_B,2.2.2.2,100,declined
            5,card_C,3.3.3.3,100,succeeded
            6,card_C,3.3.3.3,100,succeeded
            7,card_C,3.3.3.3,100,succeeded
            8,card_C,3.3.3.3,100,succeeded
            9,card_C,3.3.3.3,100,succeeded
            garbage line
            """;
        check("part1: dense declines flagged, spread-out and successes not",
                flagCards(parse(p1)), List.of("card_A"));

        String boundary = """
            0,card_D,4.4.4.4,100,declined
            15,card_D,4.4.4.4,100,declined
            30,card_D,4.4.4.4,100,declined
            45,card_D,4.4.4.4,100,declined
            60,card_D,4.4.4.4,100,declined
            """;
        check("part1: window boundary is inclusive (0..60)", flagCards(parse(boundary)), List.of("card_D"));

        String unsorted = """
            40,card_E,5.5.5.5,100,DECLINED
            0,card_E,5.5.5.5,100,declined
            20,card_E,5.5.5.5,100,declined
            10,card_E,5.5.5.5,100,declined
            30,card_E,5.5.5.5,100,declined
            """;
        check("part1: unsorted input + case-insensitive outcome", flagCards(parse(unsorted)), List.of("card_E"));
        check("part1: empty input", flagCards(parse("")), List.of());

        // Part 2
        StringBuilder p2 = new StringBuilder();
        for (int i = 0; i < 10; i++) p2.append(i * 20).append(",c").append(i).append(",9.9.9.9,100,declined\n");
        for (int i = 0; i < 10; i++) p2.append(i * 40).append(",d").append(i).append(",8.8.8.8,100,declined\n"); // spans 360s
        for (int i = 0; i < 12; i++) p2.append(i).append(",same,7.7.7.7,100,declined\n"); // same card repeated
        check("part2: 10 distinct cards in 5 min from one IP", flagIps(parse(p2.toString())), List.of("9.9.9.9"));

        System.out.println(failures == 0 ? "\nALL TESTS PASSED" : "\n" + failures + " FAILURE(S)");
    }
}
