package org.linlinjava.litemall.core.service;

import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 信用积分计算服务
 * 实现自动化的信用积分管理和等级计算
 */
@Service
public class CreditScoreService {

    @Autowired
    private LitemallUserService userService;

    /**
     * 信用积分规则枚举
     */
    public enum CreditRule {
        COMPLETE_ORDER(10, "完成交易"),
        GOOD_REVIEW(5, "收到好评"),
        BAD_REVIEW(-5, "收到差评"),
        CANCEL_ORDER(-5, "取消订单"),
        VIOLATE_GOODS(-50, "违规商品"),
        DONATE(20, "完成捐赠"),
        ON_TIME_DELIVERY(2, "准时配送"),
        LATE_DELIVERY(-10, "配送超时"),
        CERTIFICATION_PASS(50, "通过学号认证");

        private final int score;
        private final String description;

        CreditRule(int score, String description) {
            this.score = score;
            this.description = description;
        }

        public int getScore() {
            return score;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 信用等级枚举
     */
    public enum CreditLevel {
        NEWBIE(1, "新手", 0, 100),
        GOOD(2, "良好", 101, 300),
        EXCELLENT(3, "优秀", 301, 500),
        STAR_SELLER(4, "信誉商家", 501, Integer.MAX_VALUE);

        private final int level;
        private final String name;
        private final int minScore;
        private final int maxScore;

        CreditLevel(int level, String name, int minScore, int maxScore) {
            this.level = level;
            this.name = name;
            this.minScore = minScore;
            this.maxScore = maxScore;
        }

        public int getLevel() {
            return level;
        }

        public String getName() {
            return name;
        }

        public int getMinScore() {
            return minScore;
        }

        public int getMaxScore() {
            return maxScore;
        }
    }

    /**
     * 更新用户信用积分
     * 
     * @param userId 用户ID
     * @param rule   积分规则
     * @return 更新后的积分
     */
    @Transactional
    public int updateCreditScore(Integer userId, CreditRule rule) {
        // 1. 查询当前积分
        LitemallUser user = userService.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        int currentScore = user.getCreditScore() != null ? user.getCreditScore() : 100;

        // 2. 计算新积分（最低为0，无上限）
        int newScore = Math.max(0, currentScore + rule.getScore());

        // 3. 更新数据库
        user.setCreditScore(newScore);
        userService.updateById(user);

        return newScore;
    }

    /**
     * 批量更新积分（用于订单完成等场景）
     * 
     * @param userId 用户ID
     * @param rules  多个规则
     * @return 更新后的积分
     */
    @Transactional
    public int batchUpdateCreditScore(Integer userId, CreditRule... rules) {
        LitemallUser user = userService.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        int currentScore = user.getCreditScore() != null ? user.getCreditScore() : 100;
        int totalChange = 0;

        for (CreditRule rule : rules) {
            totalChange += rule.getScore();
        }

        int newScore = Math.max(0, currentScore + totalChange);
        user.setCreditScore(newScore);
        userService.updateById(user);

        return newScore;
    }

    /**
     * 计算信用等级
     * 
     * @param score 信用积分
     * @return 信用等级对象
     */
    public CreditLevel getCreditLevel(int score) {
        for (CreditLevel level : CreditLevel.values()) {
            if (score >= level.getMinScore() && score <= level.getMaxScore()) {
                return level;
            }
        }
        return CreditLevel.NEWBIE;
    }

    /**
     * 获取用户完整的信用信息
     * 
     * @param userId 用户ID
     * @return 包含积分、等级、等级名称的 Map
     */
    public Map<String, Object> getCreditInfo(Integer userId) {
        LitemallUser user = userService.findById(userId);
        if (user == null) {
            return null;
        }

        int score = user.getCreditScore() != null ? user.getCreditScore() : 100;
        CreditLevel level = getCreditLevel(score);

        Map<String, Object> info = new HashMap<>();
        info.put("userId", userId);
        info.put("totalScore", score);
        info.put("level", level.getLevel());
        info.put("levelName", level.getName());
        info.put("minScore", level.getMinScore());
        info.put("maxScore", level.getMaxScore() == Integer.MAX_VALUE ? 999999 : level.getMaxScore());

        // 计算到下一等级还需要的积分
        if (level != CreditLevel.STAR_SELLER) {
            CreditLevel nextLevel = getNextLevel(level);
            if (nextLevel != null) {
                int needScore = nextLevel.getMinScore() - score;
                info.put("needScoreForNext", Math.max(0, needScore));
                info.put("nextLevelName", nextLevel.getName());
            }
        } else {
            info.put("needScoreForNext", 0);
            info.put("nextLevelName", "已达最高等级");
        }

        return info;
    }

    /**
     * 获取下一等级
     */
    private CreditLevel getNextLevel(CreditLevel currentLevel) {
        CreditLevel[] levels = CreditLevel.values();
        for (int i = 0; i < levels.length - 1; i++) {
            if (levels[i] == currentLevel) {
                return levels[i + 1];
            }
        }
        return null;
    }

    /**
     * 检查用户是否满足特定信用等级要求
     * 
     * @param userId      用户ID
     * @param requiredLevel 需要的等级
     * @return true 如果满足要求
     */
    public boolean checkCreditLevel(Integer userId, CreditLevel requiredLevel) {
        LitemallUser user = userService.findById(userId);
        if (user == null) {
            return false;
        }

        int score = user.getCreditScore() != null ? user.getCreditScore() : 100;
        CreditLevel currentLevel = getCreditLevel(score);

        return currentLevel.getLevel() >= requiredLevel.getLevel();
    }
}
