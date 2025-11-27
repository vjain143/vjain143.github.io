package practice;

import java.util.HashMap;

public class TwoSum1 {
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
            HashMap<Integer,Integer> map = new HashMap();
            for(int i=0; i<nums.length; i++){
                int delta = target - nums[i];
                if(map.containsKey(delta)){
                    return new int[] {map.get(delta), i};
                }
                map.put(nums[i],i);
            }

            return null;
        }

        public static void main(String[] args) {
            int [] nums = {2,7,11,13,15};
            int target = 13;
            TwoSum1 twoSum1 = new TwoSum1();
            int [] result = twoSum1.twoSum(nums, target);
            System.out.println(result[0] + " " + result[1]);
        }
}
