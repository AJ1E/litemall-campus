package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.SicauBuildingMapper;
import org.linlinjava.litemall.db.domain.SicauBuilding;
import org.linlinjava.litemall.db.domain.SicauBuildingExample;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 楼栋坐标服务
 */
@Service
public class SicauBuildingService {
    
    @Resource
    private SicauBuildingMapper buildingMapper;
    
    /**
     * 根据楼栋名称查询坐标
     */
    public SicauBuilding findByBuildingName(String buildingName) {
        SicauBuildingExample example = new SicauBuildingExample();
        example.or().andBuildingNameEqualTo(buildingName);
        List<SicauBuilding> buildings = buildingMapper.selectByExample(example);
        return buildings.isEmpty() ? null : buildings.get(0);
    }
    
    /**
     * 根据校区查询所有楼栋
     */
    public List<SicauBuilding> findByCampus(String campus) {
        SicauBuildingExample example = new SicauBuildingExample();
        example.or().andCampusEqualTo(campus);
        return buildingMapper.selectByExample(example);
    }
    
    /**
     * 查询所有楼栋
     */
    public List<SicauBuilding> findAll() {
        SicauBuildingExample example = new SicauBuildingExample();
        example.setOrderByClause("campus, building_name");
        return buildingMapper.selectByExample(example);
    }
}
