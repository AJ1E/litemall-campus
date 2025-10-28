package org.linlinjava.litemall.db.service;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.SicauDonationPointMapper;
import org.linlinjava.litemall.db.domain.SicauDonationPoint;
import org.linlinjava.litemall.db.domain.SicauDonationPointExample;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * 捐赠站点服务
 */
@Service
public class SicauDonationPointService {
    
    @Resource
    private SicauDonationPointMapper donationPointMapper;
    
    /**
     * 根据校区查询捐赠站点列表
     * @param campus 校区（雅安本部/成都校区），传null返回全部
     * @return 捐赠站点列表
     */
    public List<SicauDonationPoint> queryByCampus(String campus) {
        SicauDonationPointExample example = new SicauDonationPointExample();
        SicauDonationPointExample.Criteria criteria = example.createCriteria();
        
        // 只返回开放的站点
        criteria.andIsActiveEqualTo(true);
        
        // 按校区筛选
        if (!StringUtils.isEmpty(campus)) {
            criteria.andCampusEqualTo(campus);
        }
        
        return donationPointMapper.selectByExample(example);
    }
    
    /**
     * 根据ID查询捐赠站点
     */
    public SicauDonationPoint findById(Integer id) {
        return donationPointMapper.selectByPrimaryKey(id);
    }
    
    /**
     * 查询所有捐赠站点（管理后台用）
     */
    public List<SicauDonationPoint> queryAll(Integer page, Integer size) {
        PageHelper.startPage(page, size);
        SicauDonationPointExample example = new SicauDonationPointExample();
        example.setOrderByClause("add_time DESC");
        return donationPointMapper.selectByExample(example);
    }
}
