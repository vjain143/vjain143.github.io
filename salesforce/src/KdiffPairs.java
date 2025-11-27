import java.util.HashMap;
import java.util.Map;

/**
 * Given an array of integers nums and an integer k, return the number of unique k-diff pairs in the array.
 *
 * A k-diff pair is an integer pair (nums[i], nums[j]), where the following are true:
 *
 * 0 <= i, j < nums.length
 * i != j
 * |nums[i] - nums[j]| == k
 * Notice that |val| denotes the absolute value of val.
 *
 *
 *
 * Example 1:
 *
 * Input: nums = [3,1,4,1,5], k = 2
 * Output: 2
 * Explanation: There are two 2-diff pairs in the array, (1, 3) and (3, 5).
 * Although we have two 1s in the input, we should only return the number of unique pairs.
 * Example 2:
 *
 * Input: nums = [1,2,3,4,5], k = 1
 * Output: 4
 * Explanation: There are four 1-diff pairs in the array, (1, 2), (2, 3), (3, 4) and (4, 5).
 * Example 3:
 *
 * Input: nums = [1,3,1,5,4], k = 0
 * Output: 1
 * Explanation: There is one 0-diff pair in the array, (1, 1).
 */
public class KdiffPairs {

    public int findPairs(int[] nums, int k) {
        if (nums == null || k < 0) return 0;

        if (k == 0) {
            /**
             * This block handles the case k == 0 (pairs where the absolute difference is zero — i.e., two equal numbers).
             * Purpose: count how many distinct values appear at least twice in nums.
             * Data structures:
             * seen (a HashSet) holds values encountered so far.
             * duplicates (a HashSet) holds values that have been seen more than once.
             * Loop behavior:
             * For each n in nums, seen.add(n) attempts to insert n.
             * If add returns false (meaning n was already present), n is added to duplicates.
             * Return: duplicates.size() — the number of unique values that appear at least twice (i.e., the number of unique 0-diff pairs).
             * Complexity: O(n) time, O(n) extra space.
             * Note: Using HashSet.add’s boolean return is the concise way to detect first vs subsequent occurrences.
             */
            // count values that appear at least twice
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            java.util.Set<Integer> duplicates = new java.util.HashSet<>();
            for (int n : nums) {
                if (!seen.add(n)) {
                    duplicates.add(n);
                }
            }
            return duplicates.size();
        } else {
            /**
             * This code handles the case when k > 0 and counts unique k-diff pairs.
             * seen is a set of numbers already processed.
             * pairs is a set that stores one canonical value per found pair so duplicates aren't counted.
             * Loop behavior for each number n:
             * If seen contains n - k, then a pair (n - k, n) exists; add n - k to pairs.
             * If seen contains n + k, then a pair (n, n + k) exists; add n to pairs.
             * Add n to seen for future checks.
             * At the end, pairs.size() is the number of unique k-diff pairs found.
             * Short example: with nums = [3,1,4,1,5] and k = 2, the loop finds (1,3) and (3,5) and pairs.size() becomes 2.
             */
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            java.util.Set<Integer> pairs = new java.util.HashSet<>();
            for (int n : nums) {
                if (seen.contains(n - k)) {
                    pairs.add(n - k); // pair (n-k, n)
                }
                if (seen.contains(n + k)) {
                    pairs.add(n);     // pair (n, n+k)
                }
                seen.add(n);
            }
            return pairs.size();
        }
    }

    public static void main(String[] args) {
        KdiffPairs kdiffPairs = new KdiffPairs();
        int[] nums = {3,1,4,1,5,7};
        //int[] nums = {1,3,1,5,4};
        int k = 2;
        int result = kdiffPairs.findPairs(nums, k);
        System.out.println(result);
    }

}
