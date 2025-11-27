import java.util.HashMap;
import java.util.Map;

public class TwoSum {
    /**
     * Example 1:
     * Input: nums = [2,7,11,15], target = 9
     * Output: [0,1]
     * Explanation: Because nums[0] + nums[1] == 9, we return [0, 1].
     * Example 2:
     * Input: nums = [3,2,4], target = 6
     * Output: [1,2]
     * Example 3:
     * Input: nums = [3,3], target = 6
     * Output: [0,1]
     *
     * @param nums
     * @param target
     * @return
     */
    public int[] twoSum(int[] nums, int target) {
        if (nums == null || nums.length < 2) {
            throw new IllegalArgumentException("nums must contain at least two elements");
        }
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[] { map.get(complement), i };
            }
            map.put(nums[i], i);
        }
        throw new IllegalArgumentException("No two sum solution");
    }

    public static void main(String[] args) {
        TwoSum twoSum = new TwoSum();
        int[] nums = {3,2,4}; //{1, 2, 7, 11, 15}; //{3,2,4};
        int target = 6;
        int[] result = twoSum.twoSum(nums, target);
        System.out.println(result[0] + " " + result[1]);
    }
}