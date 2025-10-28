package org.linlinjava.litemall.db.util;

import org.linlinjava.litemall.db.domain.SicauBuilding;
import org.linlinjava.litemall.db.service.SicauBuildingService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 楼栋坐标工具类
 * 从数据库加载楼栋坐标信息
 */
@Component
public class BuildingCoordinates {
    
    @Resource
    private SicauBuildingService buildingService;
    
    private static SicauBuildingService staticBuildingService;
    
    // 缓存楼栋坐标
    private static final Map<String, double[]> COORDINATES_CACHE = new HashMap<>();
    
    @PostConstruct
    public void init() {
        staticBuildingService = this.buildingService;
        // 预加载所有楼栋坐标到缓存
        refreshCache();
    }
    
    /**
     * 刷新缓存
     */
    public static void refreshCache() {
        if (staticBuildingService != null) {
            COORDINATES_CACHE.clear();
            List<SicauBuilding> buildings = staticBuildingService.findAll();
            for (SicauBuilding building : buildings) {
                COORDINATES_CACHE.put(building.getBuildingName(), 
                    new double[]{
                        building.getLatitude().doubleValue(),
                        building.getLongitude().doubleValue()
                    });
            }
        }
    }
    
    /**
     * 获取楼栋坐标
     * @param buildingName 楼栋名称
     * @return [纬度, 经度] 如果未找到返回 null
     */
    public static double[] getCoordinates(String buildingName) {
        if (buildingName == null || buildingName.trim().isEmpty()) {
            return null;
        }
        
        // 1. 精确匹配（从缓存）
        if (COORDINATES_CACHE.containsKey(buildingName)) {
            return COORDINATES_CACHE.get(buildingName);
        }
        
        // 2. 规范化匹配（去掉"栋"后缀）
        String normalized = buildingName.replace("栋", "");
        if (COORDINATES_CACHE.containsKey(normalized)) {
            return COORDINATES_CACHE.get(normalized);
        }
        
        // 3. 反向规范化（加上"栋"）
        if (!buildingName.endsWith("栋")) {
            String withSuffix = buildingName + "栋";
            if (COORDINATES_CACHE.containsKey(withSuffix)) {
                return COORDINATES_CACHE.get(withSuffix);
            }
        }
        
        // 4. 模糊匹配
        for (Map.Entry<String, double[]> entry : COORDINATES_CACHE.entrySet()) {
            if (entry.getKey().contains(buildingName) || buildingName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 5. 数据库实时查询（缓存未命中）
        if (staticBuildingService != null) {
            SicauBuilding building = staticBuildingService.findByBuildingName(buildingName);
            if (building != null) {
                double[] coords = new double[]{
                    building.getLatitude().doubleValue(),
                    building.getLongitude().doubleValue()
                };
                // 更新缓存
                COORDINATES_CACHE.put(buildingName, coords);
                return coords;
            }
        }
        
        return null;
    }
    
    /**
     * 判断楼栋是否存在
     */
    public static boolean exists(String buildingName) {
        return getCoordinates(buildingName) != null;
    }
    
    /**
     * 获取所有楼栋名称
     */
    public static String[] getAllBuildingNames() {
        return COORDINATES_CACHE.keySet().toArray(new String[0]);
    }
    
    /**
     * 获取楼栋总数
     */
    public static int size() {
        return COORDINATES_CACHE.size();
    }
}
