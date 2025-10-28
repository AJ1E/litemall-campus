package org.linlinjava.litemall.wx.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.SicauDonation;
import org.linlinjava.litemall.db.domain.SicauDonationPoint;
import org.linlinjava.litemall.db.service.SicauDonationPointService;
import org.linlinjava.litemall.db.service.SicauDonationService;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公益捐赠接口
 */
@RestController
@RequestMapping("/wx/donation")
@Validated
public class WxDonationController {
    private final Log logger = LogFactory.getLog(WxDonationController.class);
    
    @Autowired
    private SicauDonationService donationService;
    
    @Autowired
    private SicauDonationPointService donationPointService;
    
    /**
     * 提交捐赠申请
     */
    @PostMapping("/submit")
    public Object submit(@LoginUser Integer userId,
                         @RequestBody DonationSubmitRequest request) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        
        try {
            Integer donationId = donationService.submit(
                userId,
                request.getCategory(),
                request.getQuantity(),
                request.getImages(),
                request.getPickupType(),
                request.getPickupAddress(),
                request.getPickupTime()
            );
            
            Map<String, Object> data = new HashMap<>();
            data.put("donationId", donationId);
            
            return ResponseUtil.ok(data);
        } catch (Exception e) {
            logger.error("提交捐赠失败", e);
            return ResponseUtil.fail(500, e.getMessage());
        }
    }
    
    /**
     * 查询捐赠站点列表
     */
    @GetMapping("/points")
    public Object getPoints(@RequestParam(required = false) String campus) {
        List<SicauDonationPoint> points = donationPointService.queryByCampus(campus);
        return ResponseUtil.ok(points);
    }
    
    /**
     * 我的捐赠记录
     */
    @GetMapping("/myList")
    public Object getMyList(@LoginUser Integer userId,
                           @RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "10") Integer size) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        
        List<SicauDonation> donations = donationService.queryByUserId(userId, page, size);
        
        Map<String, Object> data = new HashMap<>();
        data.put("list", donations);
        data.put("page", page);
        data.put("size", size);
        
        return ResponseUtil.ok(data);
    }
    
    /**
     * 捐赠详情
     */
    @GetMapping("/detail")
    public Object getDetail(@RequestParam @NotNull Integer id) {
        SicauDonation donation = donationService.findById(id);
        if (donation == null) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(donation);
    }
    
    /**
     * 捐赠申请请求体
     */
    public static class DonationSubmitRequest {
        @NotNull(message = "分类不能为空")
        private Integer category;
        
        @NotNull(message = "数量不能为空")
        private Integer quantity;
        
        @NotNull(message = "请上传物品照片")
        private List<String> images;
        
        @NotNull(message = "取件方式不能为空")
        private Integer pickupType;
        
        private String pickupAddress;
        
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime pickupTime;
        
        // Getters and setters
        public Integer getCategory() {
            return category;
        }
        
        public void setCategory(Integer category) {
            this.category = category;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
        
        public List<String> getImages() {
            return images;
        }
        
        public void setImages(List<String> images) {
            this.images = images;
        }
        
        public Integer getPickupType() {
            return pickupType;
        }
        
        public void setPickupType(Integer pickupType) {
            this.pickupType = pickupType;
        }
        
        public String getPickupAddress() {
            return pickupAddress;
        }
        
        public void setPickupAddress(String pickupAddress) {
            this.pickupAddress = pickupAddress;
        }
        
        public LocalDateTime getPickupTime() {
            return pickupTime;
        }
        
        public void setPickupTime(LocalDateTime pickupTime) {
            this.pickupTime = pickupTime;
        }
    }
}
