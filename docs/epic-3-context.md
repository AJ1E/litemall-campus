# Epic 3 技术上下文：交易流程与支付

**Epic ID**: 3  
**Epic 标题**: 交易流程与支付  
**优先级**: P0  
**预估工时**: 56 小时  
**依赖关系**: Epic 2（商品发布与管理）  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)

---

## 1. Epic 概述

本 Epic 实现完整的交易闭环，从商品详情页浏览、下单支付，到订单状态流转、互评反馈，以及举报申诉机制。包括：
- 商品详情页展示
- 微信支付下单
- 订单状态自动流转（待发货 → 待收货 → 待评价 → 已完成）
- 取消订单和退款
- 自提功能（买卖双方聊天协商）
- 互评系统（5 星评分 + 标签 + 文字）
- 举报与申诉机制

这是平台的核心交易流程，完成后用户可进行端到端的二手交易。

---

## 2. 架构决策引用

基于 `architecture.md` 中的以下关键决策：

### ADR-005: 微信支付集成
- **支付方式**: 微信小程序 JSAPI 支付
- **安全措施**: 服务端签名验证、金额二次校验
- **退款机制**: 支持全额退款（调用微信退款 API）
- **手续费**: 0.6%/笔（由平台承担）

### ADR-006: 订单状态机设计
- **状态流转**: 
  ```
  创建 → 待付款(101) → 待发货(201) → 待收货(301) → 待评价(401) → 已完成(402)
                 ↓                                              ↓
              已取消(102)                                  已取消(103)
  ```
- **超时机制**: 
  - 待付款 30 分钟未支付自动取消
  - 待发货 24 小时未发货推送提醒
  - 待收货 7 天自动确认收货
  - 待评价 15 天自动关闭

### 技术栈
- **后端框架**: Spring Boot 2.7.x
- **支付SDK**: WxJava（微信支付 Java SDK）
- **定时任务**: Spring `@Scheduled`（Cron 表达式）
- **消息推送**: 微信模板消息

---

## 3. 数据库变更

### 3.1 复用 litemall 现有表

#### litemall_order (订单主表)
已存在字段复用：
- `id`, `user_id`, `order_sn`, `order_status`, `consignee`, `mobile`, `address`
- `goods_price`, `freight_price`, `actual_price`, `add_time`, `update_time`

**新增字段**:
```sql
ALTER TABLE `litemall_order` 
ADD COLUMN `delivery_type` TINYINT DEFAULT 1 COMMENT '配送方式: 1-学生快递员, 2-自提' AFTER `freight_price`,
ADD COLUMN `pickup_code` VARCHAR(4) COMMENT '自提取件码（4位数字）' AFTER `delivery_type`,
ADD COLUMN `courier_id` INT COMMENT '快递员用户ID（配送方式=1时填写）' AFTER `pickup_code`,
ADD COLUMN `seller_id` INT NOT NULL COMMENT '卖家用户ID' AFTER `user_id`,
ADD COLUMN `cancel_reason` VARCHAR(200) COMMENT '取消原因' AFTER `order_status`,
ADD COLUMN `ship_time` DATETIME COMMENT '发货时间' AFTER `add_time`,
ADD COLUMN `confirm_time` DATETIME COMMENT '确认收货时间' AFTER `ship_time`,
ADD INDEX `idx_seller_id` (`seller_id`),
ADD INDEX `idx_courier_id` (`courier_id`);
```

#### litemall_order_goods (订单商品关联表)
复用现有字段，无需修改。

### 3.2 新增表：sicau_comment (互评表)

```sql
CREATE TABLE `sicau_comment` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `order_id` INT NOT NULL COMMENT '订单ID',
  `from_user_id` INT NOT NULL COMMENT '评价者用户ID',
  `to_user_id` INT NOT NULL COMMENT '被评价者用户ID',
  `role` TINYINT NOT NULL COMMENT '评价者角色: 1-买家评卖家, 2-卖家评买家',
  `rating` TINYINT NOT NULL COMMENT '评分: 1-5星',
  `tags` JSON COMMENT '标签（数组）["描述相符","态度友好"]',
  `content` VARCHAR(500) COMMENT '文字评价',
  `reply` VARCHAR(500) COMMENT '回复评价（被评价者）',
  `is_anonymous` BOOLEAN DEFAULT FALSE COMMENT '是否匿名评价',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_to_user_id` (`to_user_id`),
  UNIQUE KEY `uk_order_from_role` (`order_id`, `from_user_id`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='买卖双方互评表';
```

### 3.3 新增表：sicau_report (举报申诉表)

```sql
CREATE TABLE `sicau_report` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `order_id` INT NOT NULL COMMENT '订单ID',
  `reporter_id` INT NOT NULL COMMENT '举报人用户ID',
  `reported_id` INT NOT NULL COMMENT '被举报人用户ID',
  `type` TINYINT NOT NULL COMMENT '举报类型: 1-描述不符, 2-质量问题, 3-虚假发货, 4-其他',
  `reason` TEXT NOT NULL COMMENT '举报原因详细描述',
  `images` JSON COMMENT '证据图片URL数组',
  `status` TINYINT DEFAULT 0 COMMENT '处理状态: 0-待处理, 1-处理中, 2-已解决, 3-已驳回',
  `handler_admin_id` INT COMMENT '处理管理员ID',
  `handle_result` TEXT COMMENT '处理结果说明',
  `handle_time` DATETIME COMMENT '处理时间',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_reporter_id` (`reporter_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单举报申诉表';
```

### 3.4 新增表：sicau_order_refund (退款记录表)

```sql
CREATE TABLE `sicau_order_refund` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `order_id` INT NOT NULL COMMENT '订单ID',
  `refund_sn` VARCHAR(64) NOT NULL COMMENT '退款单号',
  `refund_amount` DECIMAL(10,2) NOT NULL COMMENT '退款金额',
  `refund_reason` VARCHAR(200) COMMENT '退款原因',
  `refund_type` TINYINT NOT NULL COMMENT '退款类型: 1-用户主动取消, 2-超时未支付, 3-举报退款',
  `refund_status` TINYINT DEFAULT 0 COMMENT '退款状态: 0-待退款, 1-退款中, 2-退款成功, 3-退款失败',
  `refund_time` DATETIME COMMENT '退款成功时间',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_refund_sn` (`refund_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单退款记录表';
```

---

## 4. 核心代码实现指导

### 4.1 微信支付下单服务

创建 `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/service/WxPayService.java`：

```java
package org.linlinjava.litemall.wx.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.github.binarywang.wxpay.bean.order.WxPayMpOrderResult;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.service.WxPayService;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 微信支付服务
 */
@Service
public class WxOrderPayService {
    
    @Autowired
    private WxPayService wxPayService;
    
    @Autowired
    private WxMaService wxMaService;
    
    /**
     * 创建微信支付订单
     * @param order 订单对象
     * @param openid 用户openid
     * @return 支付参数（给小程序端调起支付）
     */
    public WxPayMpOrderResult createPayOrder(LitemallOrder order, String openid) throws Exception {
        // 1. 构建微信支付请求
        WxPayUnifiedOrderRequest request = new WxPayUnifiedOrderRequest();
        request.setOutTradeNo(order.getOrderSn()); // 商户订单号
        request.setOpenid(openid);
        request.setBody("校园闲置物品-" + order.getOrderSn());
        request.setTotalFee(order.getActualPrice().multiply(new BigDecimal("100")).intValue()); // 转为分
        request.setSpbillCreateIp("127.0.0.1");
        request.setNotifyUrl("https://api.xxx.com/wx/order/payNotify"); // 支付回调地址
        request.setTradeType("JSAPI");
        
        // 2. 调用微信统一下单接口
        WxPayMpOrderResult result = wxPayService.createOrder(request);
        
        return result;
    }
    
    /**
     * 处理支付回调
     * @param xmlData 微信回调XML数据
     * @return 是否处理成功
     */
    public boolean handlePayNotify(String xmlData) {
        try {
            // 1. 验证签名
            WxPayOrderNotifyResult notifyResult = wxPayService.parseOrderNotifyResult(xmlData);
            
            // 2. 校验订单金额
            String orderSn = notifyResult.getOutTradeNo();
            LitemallOrder order = orderService.findByOrderSn(orderSn);
            
            int dbAmount = order.getActualPrice().multiply(new BigDecimal("100")).intValue();
            int wxAmount = notifyResult.getTotalFee();
            
            if (dbAmount != wxAmount) {
                logger.error("订单金额校验失败: {} vs {}", dbAmount, wxAmount);
                return false;
            }
            
            // 3. 更新订单状态
            order.setOrderStatus((short) 201); // 待发货
            order.setPayTime(LocalDateTime.now());
            orderService.update(order);
            
            // 4. 扣减商品库存
            // goodsService.reduceStock(order.getGoodsId(), 1);
            
            // 5. 推送通知给卖家
            notifyService.notifySeller(order.getSellerId(), "您有新订单，请及时发货");
            
            return true;
        } catch (Exception e) {
            logger.error("支付回调处理失败", e);
            return false;
        }
    }
}
```

### 4.2 订单状态流转定时任务

创建 `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/task/OrderStatusTask.java`：

```java
package org.linlinjava.litemall.wx.task;

import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单状态自动流转定时任务
 */
@Component
public class OrderStatusTask {
    
    @Autowired
    private LitemallOrderService orderService;
    
    @Autowired
    private NotifyService notifyService;
    
    /**
     * 每小时扫描一次超时订单
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void checkTimeoutOrders() {
        LocalDateTime now = LocalDateTime.now();
        
        // 1. 待付款超过30分钟自动取消
        List<LitemallOrder> unpaidOrders = orderService.queryByStatus((short) 101);
        for (LitemallOrder order : unpaidOrders) {
            if (order.getAddTime().plusMinutes(30).isBefore(now)) {
                order.setOrderStatus((short) 102); // 已取消
                order.setCancelReason("超时未支付");
                orderService.update(order);
            }
        }
        
        // 2. 待发货超过24小时推送提醒
        List<LitemallOrder> unshippedOrders = orderService.queryByStatus((short) 201);
        for (LitemallOrder order : unshippedOrders) {
            if (order.getPayTime().plusHours(24).isBefore(now)) {
                notifyService.notifySeller(order.getSellerId(), "订单" + order.getOrderSn() + "请尽快发货");
            }
        }
        
        // 3. 待收货超过7天自动确认收货
        List<LitemallOrder> unconfirmedOrders = orderService.queryByStatus((short) 301);
        for (LitemallOrder order : unconfirmedOrders) {
            if (order.getShipTime().plusDays(7).isBefore(now)) {
                order.setOrderStatus((short) 401); // 待评价
                order.setConfirmTime(now);
                orderService.update(order);
                
                // 资金结算给卖家
                settleToSeller(order);
            }
        }
        
        // 4. 待评价超过15天自动关闭
        List<LitemallOrder> uncommentedOrders = orderService.queryByStatus((short) 401);
        for (LitemallOrder order : uncommentedOrders) {
            if (order.getConfirmTime().plusDays(15).isBefore(now)) {
                order.setOrderStatus((short) 402); // 已完成
                orderService.update(order);
            }
        }
    }
    
    /**
     * 资金结算给卖家（实际应对接微信企业付款API）
     */
    private void settleToSeller(LitemallOrder order) {
        // TODO: 调用微信企业付款API
        logger.info("资金结算给卖家: orderId={}, sellerId={}, amount={}", 
            order.getId(), order.getSellerId(), order.getActualPrice());
    }
}
```

### 4.3 互评服务

创建 `litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauCommentService.java`：

```java
package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.core.service.CreditScoreService;
import org.linlinjava.litemall.db.dao.SicauCommentMapper;
import org.linlinjava.litemall.db.domain.SicauComment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 互评服务
 */
@Service
public class SicauCommentService {
    
    @Resource
    private SicauCommentMapper commentMapper;
    
    @Autowired
    private CreditScoreService creditScoreService;
    
    /**
     * 提交评价
     * @param comment 评价对象
     */
    @Transactional
    public void addComment(SicauComment comment) {
        comment.setAddTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        comment.setDeleted(false);
        commentMapper.insertSelective(comment);
        
        // 根据评分更新信用积分
        if (comment.getRating() >= 5) {
            // 5星好评 +5分
            creditScoreService.updateCreditScore(
                comment.getToUserId(), 
                CreditScoreService.CreditRule.GOOD_REVIEW
            );
        } else if (comment.getRating() <= 2) {
            // 1-2星差评 -5分
            creditScoreService.updateCreditScore(
                comment.getToUserId(), 
                CreditScoreService.CreditRule.BAD_REVIEW
            );
        }
    }
    
    /**
     * 查询订单的所有评价
     */
    public List<SicauComment> queryByOrderId(Integer orderId) {
        return commentMapper.selectByOrderId(orderId);
    }
    
    /**
     * 查询用户收到的评价
     */
    public List<SicauComment> queryByToUserId(Integer userId, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        return commentMapper.selectByToUserId(userId);
    }
    
    /**
     * 检查是否双方都已评价
     */
    public boolean isBothCommented(Integer orderId) {
        List<SicauComment> comments = queryByOrderId(orderId);
        return comments.size() >= 2;
    }
}
```

---

## 5. API 契约定义

### 5.1 GET /wx/goods/detail - 商品详情页

**请求参数**:
- `id`: 商品ID

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "goods": {
      "id": 1,
      "name": "高等数学A（第七版）上册",
      "picUrl": "https://...",
      "gallery": ["https://...", "https://..."],
      "retailPrice": 50.00,
      "originalPrice": 89.00,
      "newness": 2,
      "brief": "仅用过一学期，无笔记无划线",
      "userId": 123
    },
    "seller": {
      "id": 123,
      "nickname": "川农学子",
      "avatar": "https://...",
      "creditLevel": 3,
      "creditLevelName": "优秀",
      "totalOrders": 12
    },
    "comments": [
      {
        "fromUserNickname": "买家A",
        "rating": 5,
        "tags": ["描述相符", "态度友好"],
        "content": "商品很新，卖家人很好！",
        "addTime": "2025-10-20"
      }
    ]
  }
}
```

### 5.2 POST /wx/order/create - 创建订单

**请求体**:
```json
{
  "goodsId": 1,
  "deliveryType": 1,
  "consignee": "张三",
  "mobile": "13800138000",
  "address": "7舍A栋 501"
}
```

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "orderId": 100,
    "orderSn": "20251027123456",
    "actualPrice": 52.00
  }
}
```

### 5.3 POST /wx/order/pay - 发起支付

**请求体**:
```json
{
  "orderId": 100
}
```

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "timeStamp": "1635331200",
    "nonceStr": "abcd1234",
    "package": "prepay_id=wx20251027...",
    "signType": "RSA",
    "paySign": "..."
  }
}
```

### 5.4 POST /wx/order/cancel - 取消订单

**请求体**:
```json
{
  "orderId": 100,
  "reason": "误操作"
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "取消成功，退款将在1-3个工作日到账"
}
```

### 5.5 POST /wx/comment/add - 提交评价

**请求体**:
```json
{
  "orderId": 100,
  "toUserId": 123,
  "role": 1,
  "rating": 5,
  "tags": ["描述相符", "态度友好"],
  "content": "商品很新，卖家人很好！"
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "评价成功"
}
```

### 5.6 POST /wx/order/report - 举报订单

**请求体**:
```json
{
  "orderId": 100,
  "type": 1,
  "reason": "商品描述与实物不符，图片是全新的，实际有明显使用痕迹",
  "images": ["https://...", "https://..."]
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "举报成功，管理员将在24小时内处理"
}
```

---

## 6. 配置文件变更

### 6.1 application-wx.yml

```yaml
# 微信支付配置
wx:
  pay:
    appId: ${WX_PAY_APP_ID}
    mchId: ${WX_PAY_MCH_ID}
    mchKey: ${WX_PAY_MCH_KEY}
    notifyUrl: https://api.xxx.com/wx/order/payNotify
    
# 订单超时配置
order:
  timeout:
    unpaid: 30  # 待付款超时（分钟）
    unshipped: 24  # 待发货提醒（小时）
    unconfirmed: 7  # 待收货自动确认（天）
    uncommented: 15  # 待评价自动关闭（天）
```

---

## 7. 前端开发要点

### 7.1 小程序端关键文件

- `pages/goods/detail/detail.js` - 商品详情页
- `pages/order/create/create.js` - 确认订单页
- `pages/order/list/list.js` - 订单列表页
- `pages/order/detail/detail.js` - 订单详情页
- `pages/comment/add/add.js` - 评价页面

### 7.2 微信支付调起

```javascript
// pages/order/create/create.js
async function pay(orderId) {
  // 1. 调用后端接口获取支付参数
  const res = await wx.request({
    url: 'https://api.xxx.com/wx/order/pay',
    method: 'POST',
    data: { orderId }
  });
  
  const payData = res.data.data;
  
  // 2. 调起微信支付
  wx.requestPayment({
    timeStamp: payData.timeStamp,
    nonceStr: payData.nonceStr,
    package: payData.package,
    signType: payData.signType,
    paySign: payData.paySign,
    success: () => {
      wx.showToast({ title: '支付成功' });
      wx.navigateTo({ url: '/pages/order/detail/detail?id=' + orderId });
    },
    fail: () => {
      wx.showToast({ title: '支付失败', icon: 'none' });
    }
  });
}
```

---

## 8. 测试策略

### 8.1 单元测试

- 微信支付签名验证测试
- 订单金额计算测试（商品价格 + 配送费）
- 订单状态流转逻辑测试

### 8.2 集成测试

- 完整下单支付流程测试（创建订单 → 支付 → 回调 → 状态更新）
- 取消订单退款流程测试
- 互评后信用积分更新测试

### 8.3 性能测试

- 支付回调并发测试（防止重复支付）
- 订单列表加载性能（1000+ 订单分页加载 < 500ms）

---

## 9. 依赖关系

### 前置条件
- Epic 2 已完成（需要有商品数据）
- 微信支付已配置（商户号、密钥）
- 微信小程序已认证（才能使用支付功能）

### 后续依赖
- **Epic 4 (快递员配送)** 依赖订单表的 `courier_id` 字段
- **Epic 5 (限时拍卖)** 复用订单创建和支付流程

---

## 10. 风险提示

1. **支付安全**: 必须进行金额二次校验，防止前端篡改价格
2. **并发问题**: 支付回调可能重复，需做幂等性处理
3. **退款时效**: 微信退款 1-3 个工作日到账，需向用户说明
4. **恶意评价**: 需要人工审核机制，防止刷好评或恶意差评
5. **举报滥用**: 恶意举报3次以上的用户需冻结举报权限

---

## 11. Story 任务分解

### Story 3.1: 商品详情页 (8h)
- Task 1: 创建 `WxGoodsController.detail()` 接口
- Task 2: 查询卖家信用信息（关联 `LitemallUser` 和 `SicauStudentAuth`）
- Task 3: 查询商品历史评价（前 10 条）
- Task 4: 前端详情页开发（图片轮播、卖家卡片）
- Task 5: 添加"收藏"和"立即购买"按钮

### Story 3.2: 下单与支付 (12h)
- Task 1: 创建 `WxOrderController.create()` 接口
- Task 2: 生成订单号（时间戳 + 随机数）
- Task 3: 集成微信支付 SDK
- Task 4: 实现支付回调接口 `payNotify()`
- Task 5: 金额二次校验（防篡改）
- Task 6: 前端确认订单页开发
- Task 7: 调起微信支付

### Story 3.3: 订单状态流转 (10h)
- Task 1: 创建定时任务 `OrderStatusTask`
- Task 2: 实现待付款超时取消（30 分钟）
- Task 3: 实现待发货超时提醒（24 小时）
- Task 4: 实现待收货自动确认（7 天）
- Task 5: 实现待评价自动关闭（15 天）
- Task 6: 微信模板消息推送

### Story 3.4: 取消订单 (6h)
- Task 1: 创建 `WxOrderController.cancel()` 接口
- Task 2: 判断取消时间窗口（5 分钟内）
- Task 3: 调用微信退款 API
- Task 4: 扣除买家 5 积分
- Task 5: 创建退款记录表 `sicau_order_refund`

### Story 3.5: 自提功能 (8h)
- Task 1: 订单创建时生成 4 位取件码
- Task 2: 前端聊天页面开发（复用微信客服消息）
- Task 3: 位置分享功能（选择自提地点）
- Task 4: 自提订单确认收货流程

### Story 3.6: 互评系统 (8h)
- Task 1: 创建 `sicau_comment` 表
- Task 2: 创建 `SicauCommentService`
- Task 3: 实现 `WxCommentController.add()` 接口
- Task 4: 评价后更新信用积分
- Task 5: 前端评价页开发（星级 + 标签 + 文字）
- Task 6: 查看历史评价接口

### Story 3.7: 举报与申诉 (4h)
- Task 1: 创建 `sicau_report` 表
- Task 2: 实现 `WxOrderController.report()` 接口
- Task 3: 管理后台举报处理接口
- Task 4: 前端举报页面开发

---

## 12. 验收清单

- [ ] 商品详情页展示完整信息（图片、价格、卖家信用）
- [ ] 可成功创建订单并调起微信支付
- [ ] 支付成功后订单状态变更为"待发货"
- [ ] 定时任务正常运行（超时订单自动处理）
- [ ] 取消订单后可正常退款
- [ ] 买卖双方可互相评价
- [ ] 评价后信用积分自动更新
- [ ] 举报功能可正常提交
- [ ] 所有 API 无编译错误

---

**Epic 状态**: 准备就绪，等待开发  
**下一步**: 由 bmm-dev 开发者开始实施
