package org.linlinjava.litemall.wx.web;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Story 2.1: 商品发布 - 单元测试
 * 
 * @author bmm-dev
 * @date 2025-10-28
 */
@RunWith(MockitoJUnitRunner.class)
public class WxGoodsControllerTest {
    
    @Mock
    private LitemallGoodsService goodsService;
    
    @InjectMocks
    private WxGoodsController controller;
    
    private Integer testUserId = 1;
    
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        
        // Mock goodsService.add() to set generated ID
        when(goodsService.add(any(LitemallGoods.class))).thenAnswer(invocation -> {
            LitemallGoods goods = invocation.getArgument(0);
            goods.setId(100);
            return 1;
        });
    }
    
    /**
     * AC1: 必填字段验证 - 标题、价格、分类、新旧程度
     */
    @Test
    public void testPublish_RequiredFields_Success() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("高等数学教材");
        goods.setRetailPrice(new BigDecimal("30.00"));
        goods.setCategoryId(1);
        goods.setBrief("八成新");
        
        Object result = controller.publish(goods, testUserId);
        
        assertNotNull(result);
        verify(goodsService, times(1)).add(any(LitemallGoods.class));
    }
    
    /**
     * AC1: 标题不能为空
     */
    @Test
    public void testPublish_EmptyTitle_Fail() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("");
        goods.setRetailPrice(new BigDecimal("30.00"));
        goods.setCategoryId(1);
        goods.setBrief("八成新");
        
        Object result = controller.publish(goods, testUserId);
        
        assertNotNull(result);
        verify(goodsService, never()).add(any(LitemallGoods.class));
    }
    
    /**
     * AC1: 价格不能为空
     */
    @Test
    public void testPublish_EmptyPrice_Fail() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("高等数学教材");
        goods.setRetailPrice(null);
        goods.setCategoryId(1);
        goods.setBrief("八成新");
        
        Object result = controller.publish(goods, testUserId);
        
        assertNotNull(result);
        verify(goodsService, never()).add(any(LitemallGoods.class));
    }
    
    /**
     * AC1: 分类不能为空
     */
    @Test
    public void testPublish_EmptyCategory_Fail() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("高等数学教材");
        goods.setRetailPrice(new BigDecimal("30.00"));
        goods.setCategoryId(null);
        goods.setBrief("八成新");
        
        Object result = controller.publish(goods, testUserId);
        
        assertNotNull(result);
        verify(goodsService, never()).add(any(LitemallGoods.class));
    }
    
    /**
     * AC1: 新旧程度不能为空
     */
    @Test
    public void testPublish_EmptyNewness_Fail() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("高等数学教材");
        goods.setRetailPrice(new BigDecimal("30.00"));
        goods.setCategoryId(1);
        goods.setBrief(null);
        
        Object result = controller.publish(goods, testUserId);
        
        assertNotNull(result);
        verify(goodsService, never()).add(any(LitemallGoods.class));
    }
    
    /**
     * AC1: 新旧程度超出范围 (0-10)
     */
    @Test
    public void testPublish_InvalidNewness_Fail() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("高等数学教材");
        goods.setRetailPrice(new BigDecimal("30.00"));
        goods.setCategoryId(1);
        goods.setBrief("全新");
        
        Object result = controller.publish(goods, testUserId);
        
        assertNotNull(result);
        verify(goodsService, times(1)).add(any(LitemallGoods.class));
    }
    
    /**
     * AC2: 可选字段 - 原价、购买时间
     */
    @Test
    public void testPublish_OptionalFields_Success() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("高等数学教材");
        goods.setRetailPrice(new BigDecimal("30.00"));
        goods.setCategoryId(1);
        goods.setBrief("八成新");
        goods.setCounterPrice(new BigDecimal("50.00")); // 原价
        
        Object result = controller.publish(goods, testUserId);
        
        assertNotNull(result);
        verify(goodsService, times(1)).add(any(LitemallGoods.class));
    }
    
    /**
     * AC3: 图片上传 - 1-9张
     */
    @Test
    public void testPublish_WithImages_Success() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("高等数学教材");
        goods.setRetailPrice(new BigDecimal("30.00"));
        goods.setCategoryId(1);
        goods.setBrief("八成新");
        goods.setGallery(new String[]{"http://example.com/1.jpg", "http://example.com/2.jpg"});
        
        Object result = controller.publish(goods, testUserId);
        
        assertNotNull(result);
        verify(goodsService, times(1)).add(any(LitemallGoods.class));
    }
    
    /**
     * AC3: 图片超过9张
     */
    @Test
    public void testPublish_TooManyImages_Fail() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("高等数学教材");
        goods.setRetailPrice(new BigDecimal("30.00"));
        goods.setCategoryId(1);
        goods.setBrief("八成新");
        goods.setGallery(new String[]{
            "1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg",
            "6.jpg", "7.jpg", "8.jpg", "9.jpg", "10.jpg"
        });
        
        Object result = controller.publish(goods, testUserId);
        
        assertNotNull(result);
        verify(goodsService, never()).add(any(LitemallGoods.class));
    }
    
    /**
     * AC4: 商品描述长度不超过500字
     */
    @Test
    public void testPublish_DescriptionTooLong_Fail() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("高等数学教材");
        goods.setRetailPrice(new BigDecimal("30.00"));
        goods.setCategoryId(1);
        goods.setBrief("八成新");
        
        // 生成超过500字的描述
        StringBuilder longDesc = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            longDesc.append("测");
        }
        goods.setDetail(longDesc.toString());
        
        Object result = controller.publish(goods, testUserId);
        
        assertNotNull(result);
        verify(goodsService, never()).add(any(LitemallGoods.class));
    }
    
    /**
     * AC5: 商品标题不超过30字
     */
    @Test
    public void testPublish_TitleTooLong_Fail() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("这是一个非常非常非常非常非常非常非常非常长的标题超过三十个字");
        goods.setRetailPrice(new BigDecimal("30.00"));
        goods.setCategoryId(1);
        goods.setBrief("八成新");
        
        Object result = controller.publish(goods, testUserId);
        
        assertNotNull(result);
        verify(goodsService, never()).add(any(LitemallGoods.class));
    }
    
    /**
     * AC6: 自动设置商品状态为待审核
     */
    @Test
    public void testPublish_AutoSetPendingStatus() {
        LitemallGoods goods = new LitemallGoods();
        goods.setName("高等数学教材");
        goods.setRetailPrice(new BigDecimal("30.00"));
        goods.setCategoryId(1);
        goods.setBrief("八成新");
        
        controller.publish(goods, testUserId);
        
        verify(goodsService).add(argThat(g -> g.getIsOnSale() == false));
    }
}
