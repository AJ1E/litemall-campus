package org.linlinjava.litemall.db.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 距离计算工具类
 * 用于计算校内配送距离和配送费
 * 
 * @author bmm-dev
 * @since 2025-10-28 (Epic 4 - Story 4.2)
 */
public class DistanceCalculator {
    
    /**
     * 计算两点之间的欧几里得距离（单位：km）
     * 适用于校内短距离配送（< 3km），平面坐标误差可忽略
     * 
     * @param lat1 纬度1
     * @param lon1 经度1
     * @param lat2 纬度2
     * @param lon2 经度2
     * @return 距离（km），保留2位小数
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // 纬度1度 ≈ 111 km
        // 经度1度 ≈ 111 * cos(lat) km
        // 雅安校区纬度约30°，cos(30°) ≈ 0.866
        
        double latDiff = (lat2 - lat1) * 111.0;
        double lonDiff = (lon2 - lon1) * 111.0 * Math.cos(Math.toRadians(lat1));
        
        double distance = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
        
        // 保留2位小数
        BigDecimal bd = new BigDecimal(distance);
        return bd.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
    
    /**
     * 根据距离计算配送费
     * 规则：
     * - 1km 内: 2 元
     * - 1-2km: 4 元
     * - 2-3km: 6 元
     * - > 3km: 6 元 (校内最大距离约 3km)
     * 
     * @param distance 距离（km）
     * @return 配送费（元）
     */
    public static double calculateFee(double distance) {
        if (distance <= 1.0) {
            return 2.0;
        } else if (distance <= 2.0) {
            return 4.0;
        } else {
            return 6.0;
        }
    }
    
    /**
     * 从地址字符串中提取楼栋名称
     * 支持格式：
     * - "7舍A栋 501"
     * - "雅安本部 7舍A栋 501"
     * - "信息楼 302"
     * 
     * @param address 完整地址
     * @return 楼栋名称，如果无法提取则返回 null
     */
    public static String extractBuildingName(String address) {
        if (address == null || address.trim().isEmpty()) {
            return null;
        }
        
        // 去除空格并分割
        String[] parts = address.trim().split("\\s+");
        
        // 优先匹配包含"舍"或"楼"的部分
        for (String part : parts) {
            if (part.contains("舍") || part.contains("楼") || part.contains("栋")) {
                return part;
            }
        }
        
        // 如果第一个部分不是校区名，则返回第一部分
        if (parts.length > 0 && !parts[0].contains("校区") && !parts[0].contains("本部")) {
            return parts[0];
        }
        
        // 如果第二部分存在，返回第二部分
        if (parts.length > 1) {
            return parts[1];
        }
        
        return null;
    }
}
