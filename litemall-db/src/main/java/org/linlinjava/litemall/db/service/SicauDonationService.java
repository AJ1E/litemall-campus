package org.linlinjava.litemall.db.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.SicauDonationMapper;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.SicauDonation;
import org.linlinjava.litemall.db.domain.SicauDonationExample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 公益捐赠服务
 */
@Service
public class SicauDonationService {
    
    private static final Logger logger = LoggerFactory.getLogger(SicauDonationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Resource
    private SicauDonationMapper donationMapper;
    
    @Autowired
    private LitemallUserService userService;
    
    /**
     * 提交捐赠申请
     * @param userId 用户ID
     * @param category 分类（1-衣物, 2-文具, 3-书籍, 4-其他）
     * @param quantity 数量
     * @param images 照片URL数组
     * @param pickupType 取件方式（1-自送, 2-上门）
     * @param pickupAddress 取件地址（上门时填写）
     * @param pickupTime 预约上门时间
     * @return 捐赠ID
     */
    @Transactional
    public Integer submit(Integer userId, Integer category, Integer quantity,
                          List<String> images, Integer pickupType,
                          String pickupAddress, LocalDateTime pickupTime) {
        // 1. 参数校验
        if (images == null || images.isEmpty() || images.size() > 3) {
            throw new RuntimeException("请上传 1-3 张物品照片");
        }
        
        if (pickupType == 2 && (pickupAddress == null || pickupTime == null)) {
            throw new RuntimeException("上门取件请填写地址和预约时间");
        }
        
        // 2. 创建捐赠记录
        SicauDonation donation = new SicauDonation();
        donation.setUserId(userId);
        donation.setCategory(category.byteValue());
        donation.setQuantity(quantity);
        
        try {
            donation.setImages(objectMapper.writeValueAsString(images));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("图片URL序列化失败", e);
        }
        
        donation.setPickupType(pickupType.byteValue());
        donation.setPickupAddress(pickupAddress);
        donation.setPickupTime(pickupTime);
        donation.setStatus((byte) 0); // 待审核
        donation.setAddTime(LocalDateTime.now());
        donation.setDeleted(false);
        
        donationMapper.insertSelective(donation);
        
        // 3. 推送通知（TODO: 实际应调用通知服务）
        logger.info("用户 {} 提交捐赠申请，ID: {}, 分类: {}", userId, donation.getId(), category);
        
        return donation.getId();
    }
    
    /**
     * 管理员审核捐赠
     * @param donationId 捐赠ID
     * @param auditorId 审核管理员ID
     * @param pass 是否通过
     * @param rejectReason 拒绝原因（不通过时必填）
     */
    @Transactional
    public void audit(Integer donationId, Integer auditorId, Boolean pass, String rejectReason) {
        SicauDonation donation = donationMapper.selectByPrimaryKey(donationId);
        
        if (donation == null) {
            throw new RuntimeException("捐赠记录不存在");
        }
        
        if (donation.getStatus() != 0) {
            throw new RuntimeException("该捐赠已审核过");
        }
        
        // 1. 更新审核结果
        donation.setAuditorId(auditorId);
        donation.setAuditTime(LocalDateTime.now());
        donation.setUpdateTime(LocalDateTime.now());
        
        if (pass) {
            donation.setStatus((byte) 1); // 审核通过
            donationMapper.updateByPrimaryKeySelective(donation);
            
            logger.info("捐赠 {} 审核通过，审核员: {}", donationId, auditorId);
            
            // 2. 分配志愿者（若选择上门取件）
            if (donation.getPickupType() == 2) {
                // TODO: 实际应从志愿者池中分配
                logger.info("捐赠 {} 需要上门取件，地址: {}, 时间: {}", 
                    donationId, donation.getPickupAddress(), donation.getPickupTime());
            }
            
        } else {
            if (rejectReason == null || rejectReason.trim().isEmpty()) {
                throw new RuntimeException("审核拒绝请填写拒绝原因");
            }
            
            donation.setStatus((byte) 2); // 审核拒绝
            donation.setRejectReason(rejectReason);
            donationMapper.updateByPrimaryKeySelective(donation);
            
            logger.info("捐赠 {} 审核拒绝，原因: {}", donationId, rejectReason);
        }
    }
    
    /**
     * 确认收货（志愿者或管理员操作）
     * @param donationId 捐赠ID
     */
    @Transactional
    public void finish(Integer donationId) {
        SicauDonation donation = donationMapper.selectByPrimaryKey(donationId);
        
        if (donation == null) {
            throw new RuntimeException("捐赠记录不存在");
        }
        
        if (donation.getStatus() != 1) {
            throw new RuntimeException("捐赠状态异常，当前状态: " + donation.getStatus());
        }
        
        // 1. 更新捐赠状态
        donation.setStatus((byte) 3); // 已完成
        donation.setFinishTime(LocalDateTime.now());
        donation.setUpdateTime(LocalDateTime.now());
        donationMapper.updateByPrimaryKeySelective(donation);
        
        // 2. 奖励积分（+20 分）
        // TODO: 等待 CreditScoreService 完善后对接
        logger.info("捐赠 {} 已完成，用户 {} 应获得 +20 积分", donationId, donation.getUserId());
        
        // 3. 增加捐赠次数并检查徽章
        LitemallUser user = userService.findById(donation.getUserId());
        if (user != null) {
            // 增加捐赠次数（暂时注释，等 litemall_user 表有 donation_count 字段后启用）
            // int newCount = (user.getDonationCount() != null ? user.getDonationCount() : 0) + 1;
            // user.setDonationCount(newCount);
            
            // 4. 检查并颁发徽章
            // awardBadge(user, newCount);
            
            // userService.updateById(user);
            
            logger.info("捐赠 {} 完成，用户 {} 累计捐赠次数待更新", donationId, donation.getUserId());
        }
    }
    
    /**
     * 颁发徽章
     * @param user 用户
     * @param count 捐赠次数
     */
    private void awardBadge(LitemallUser user, int count) {
        // 暂时注释，等 litemall_user 表有 badges 字段后启用
        /*
        List<String> badges = new ArrayList<>();
        String badgesJson = user.getBadges();
        if (badgesJson != null && !badgesJson.isEmpty()) {
            badges = JSON.parseArray(badgesJson, String.class);
        }
        
        boolean updated = false;
        
        // 捐赠 5 次 → 爱心大使
        if (count == 5 && !badges.contains("爱心大使")) {
            badges.add("爱心大使");
            logger.info("用户 {} 获得【爱心大使】徽章！", user.getId());
            updated = true;
        }
        
        // 捐赠 10 次 → 公益达人
        if (count == 10 && !badges.contains("公益达人")) {
            badges.add("公益达人");
            logger.info("用户 {} 获得【公益达人】徽章！", user.getId());
            updated = true;
        }
        
        // 捐赠 20 次 → 环保先锋
        if (count == 20 && !badges.contains("环保先锋")) {
            badges.add("环保先锋");
            logger.info("用户 {} 获得【环保先锋】徽章！", user.getId());
            updated = true;
        }
        
        if (updated) {
            user.setBadges(JSON.toJSONString(badges));
        }
        */
    }
    
    /**
     * 查询我的捐赠记录
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页数量
     * @return 捐赠列表
     */
    public List<SicauDonation> queryByUserId(Integer userId, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        
        SicauDonationExample example = new SicauDonationExample();
        example.createCriteria()
            .andUserIdEqualTo(userId)
            .andDeletedEqualTo(false);
        example.setOrderByClause("add_time DESC");
        
        return donationMapper.selectByExample(example);
    }
    
    /**
     * 查询待审核捐赠列表（管理后台用）
     */
    public List<SicauDonation> queryPendingAudits(Integer page, Integer size) {
        PageHelper.startPage(page, size);
        
        SicauDonationExample example = new SicauDonationExample();
        example.createCriteria()
            .andStatusEqualTo((byte) 0)
            .andDeletedEqualTo(false);
        example.setOrderByClause("add_time ASC"); // 按提交时间升序，先审核早提交的
        
        return donationMapper.selectByExample(example);
    }
    
    /**
     * 根据状态查询捐赠列表
     * @param status 状态（0-待审核, 1-审核通过, 2-审核拒绝, 3-已完成）
     */
    public List<SicauDonation> queryByStatus(Byte status, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        
        SicauDonationExample example = new SicauDonationExample();
        example.createCriteria()
            .andStatusEqualTo(status)
            .andDeletedEqualTo(false);
        example.setOrderByClause("add_time DESC");
        
        return donationMapper.selectByExample(example);
    }
    
    /**
     * 根据ID查询捐赠详情
     */
    public SicauDonation findById(Integer id) {
        return donationMapper.selectByPrimaryKey(id);
    }
    
    /**
     * 统计捐赠数量（按状态）
     */
    public int countByStatus(Byte status) {
        SicauDonationExample example = new SicauDonationExample();
        example.createCriteria()
            .andStatusEqualTo(status)
            .andDeletedEqualTo(false);
        return (int) donationMapper.countByExample(example);
    }
}
