# 用户故事 1.5 开发上下文：个人主页

**Story ID**: 1.5  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)  
**目标读者**: bmm-dev (Developer Agent)

本文档为开发人员提供实现"个人主页"功能的精确代码指导和实现路径。

---

## 1. 实现概览

### 核心设计理念
个人主页是一个**综合管理中心**，整合了用户信息展示、商品管理、订单管理和收藏夹功能。采用 Tab 切换架构，提供流畅的多功能访问体验。

### 页面架构
```
┌─────────────────────────────────┐
│  用户信息区（头像、积分、认证）    │
├─────────────────────────────────┤
│  Tab 导航栏                      │
├─────────────────────────────────┤
│  内容区（根据 Tab 动态切换）      │
│  - Tab 1: 我的发布（商品列表）    │
│  - Tab 2: 我的订单（订单列表）    │
│  - Tab 3: 收藏夹（商品列表）      │
└─────────────────────────────────┘
```

---

## 2. 后端代码实现

### 2.1 文件结构

```
litemall-wx-api/
└── src/main/java/org/linlinjava/litemall/wx/
    ├── web/
    │   ├── WxUserController.java        (扩展 - 用户信息)
    │   ├── WxGoodsController.java       (扩展 - 我的发布)
    │   └── WxOrderController.java       (扩展 - 我的订单)
    └── service/
        ├── WxGoodsService.java          (扩展)
        └── WxOrderService.java          (扩展)
```

---

## 2.2 用户信息接口

### WxUserController.java

**文件**: `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxUserController.java`

```java
@Autowired
private LitemallUserService userService;

@Autowired
private SicauStudentAuthService studentAuthService;

@Autowired
private LitemallOrderService orderService;

/**
 * 获取用户信息（个人主页专用）
 */
@GetMapping("/info")
public Object getUserInfo(@LoginUser Integer userId) {
    if (userId == null) {
        return ResponseUtil.unlogin();
    }
    
    // 查询用户基本信息
    LitemallUser user = userService.findById(userId);
    if (user == null) {
        return ResponseUtil.fail(404, "用户不存在");
    }
    
    // 查询认证状态
    SicauStudentAuth auth = studentAuthService.getByUserId(userId);
    Integer authStatus = auth != null ? auth.getStatus().intValue() : 0;
    
    // 统计成交笔数（买家或卖家已完成的订单）
    int orderCount = orderService.countCompletedOrders(userId);
    
    // 获取信用等级
    CreditLevelEnum level = CreditLevelEnum.getByScore(user.getCreditScore());
    
    Map<String, Object> data = new HashMap<>();
    data.put("id", user.getId());
    data.put("nickname", user.getNickname());
    data.put("avatar", user.getAvatar());
    data.put("creditScore", user.getCreditScore());
    data.put("creditLevel", level.getLevelName());
    data.put("authStatus", authStatus);  // 0-未认证, 1-已认证, 2-认证失败
    data.put("orderCount", orderCount);
    
    // 如果已认证，返回脱敏的学号
    if (auth != null && authStatus == 1) {
        try {
            String studentNo = aesUtil.decrypt(auth.getStudentNo());
            // 脱敏：显示前3位和后3位，中间用 * 代替
            String maskedStudentNo = studentNo.substring(0, 3) + "******" + studentNo.substring(9);
            data.put("studentNo", maskedStudentNo);
        } catch (Exception e) {
            logger.error("解密学号失败", e);
        }
    }
    
    return ResponseUtil.ok(data);
}
```

---

## 2.3 我的发布接口

### WxGoodsController.java

**文件**: `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxGoodsController.java`

```java
@Autowired
private LitemallGoodsService goodsService;

/**
 * 我的发布列表
 */
@GetMapping("/myList")
public Object getMyGoodsList(@LoginUser Integer userId,
                              @RequestParam(defaultValue = "1") Integer status,  // 1-已上架, 0-已下架
                              @RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "20") Integer limit) {
    if (userId == null) {
        return ResponseUtil.unlogin();
    }
    
    List<LitemallGoods> goodsList = goodsService.queryByUserIdAndStatus(userId, status, page, limit);
    int total = goodsService.countByUserIdAndStatus(userId, status);
    
    // 转换为前端需要的格式
    List<Map<String, Object>> items = new ArrayList<>();
    for (LitemallGoods goods : goodsList) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", goods.getId());
        item.put("picUrl", goods.getPicUrl());
        item.put("name", goods.getName());
        item.put("retailPrice", goods.getRetailPrice());
        item.put("viewCount", goods.getBrowse());  // 浏览量
        item.put("isOnSale", goods.getIsOnSale());
        item.put("createTime", goods.getAddTime());
        
        // 判断是否已售出
        boolean isSold = goods.getCounterPrice() != null && goods.getCounterPrice() > 0;
        item.put("isSold", isSold);
        
        items.add(item);
    }
    
    Map<String, Object> data = new HashMap<>();
    data.put("total", total);
    data.put("items", items);
    
    return ResponseUtil.ok(data);
}

/**
 * 上架/下架商品
 */
@PostMapping("/toggleOnSale")
public Object toggleOnSale(@LoginUser Integer userId, @RequestBody Map<String, Object> body) {
    if (userId == null) {
        return ResponseUtil.unlogin();
    }
    
    Integer goodsId = (Integer) body.get("id");
    Boolean isOnSale = (Boolean) body.get("isOnSale");
    
    if (goodsId == null || isOnSale == null) {
        return ResponseUtil.badArgument();
    }
    
    // 查询商品
    LitemallGoods goods = goodsService.findById(goodsId);
    if (goods == null) {
        return ResponseUtil.fail(404, "商品不存在");
    }
    
    // 验证权限
    if (!goods.getUserId().equals(userId)) {
        return ResponseUtil.fail(403, "无权操作此商品");
    }
    
    // 验证是否已售出
    if (goods.getCounterPrice() != null && goods.getCounterPrice() > 0) {
        return ResponseUtil.fail(400, "已售出商品不可操作");
    }
    
    // 更新上架状态
    goods.setIsOnSale(isOnSale);
    goodsService.updateById(goods);
    
    return ResponseUtil.ok("操作成功");
}

/**
 * 删除商品
 */
@PostMapping("/delete")
public Object deleteGoods(@LoginUser Integer userId, @RequestBody Map<String, Object> body) {
    if (userId == null) {
        return ResponseUtil.unlogin();
    }
    
    Integer goodsId = (Integer) body.get("id");
    if (goodsId == null) {
        return ResponseUtil.badArgument();
    }
    
    // 查询商品
    LitemallGoods goods = goodsService.findById(goodsId);
    if (goods == null) {
        return ResponseUtil.fail(404, "商品不存在");
    }
    
    // 验证权限
    if (!goods.getUserId().equals(userId)) {
        return ResponseUtil.fail(403, "无权操作此商品");
    }
    
    // 验证是否已售出
    if (goods.getCounterPrice() != null && goods.getCounterPrice() > 0) {
        return ResponseUtil.fail(400, "已售出商品不可删除");
    }
    
    // 软删除
    goods.setDeleted(true);
    goodsService.updateById(goods);
    
    return ResponseUtil.ok("删除成功");
}
```

---

### LitemallGoodsService.java 扩展

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/service/LitemallGoodsService.java`

```java
/**
 * 查询用户的商品列表
 */
public List<LitemallGoods> queryByUserIdAndStatus(Integer userId, Integer status, Integer page, Integer limit) {
    LitemallGoodsExample example = new LitemallGoodsExample();
    example.or()
        .andUserIdEqualTo(userId)
        .andIsOnSaleEqualTo(status == 1)
        .andDeletedEqualTo(false);
    example.setOrderByClause("add_time DESC");
    
    PageHelper.startPage(page, limit);
    return goodsMapper.selectByExample(example);
}

/**
 * 统计用户的商品数量
 */
public int countByUserIdAndStatus(Integer userId, Integer status) {
    LitemallGoodsExample example = new LitemallGoodsExample();
    example.or()
        .andUserIdEqualTo(userId)
        .andIsOnSaleEqualTo(status == 1)
        .andDeletedEqualTo(false);
    
    return (int) goodsMapper.countByExample(example);
}
```

---

## 2.4 我的订单接口

### WxOrderController.java

**文件**: `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxOrderController.java`

```java
@Autowired
private LitemallOrderService orderService;

@Autowired
private LitemallOrderGoodsService orderGoodsService;

/**
 * 我的订单列表
 */
@GetMapping("/myList")
public Object getMyOrderList(@LoginUser Integer userId,
                              @RequestParam(defaultValue = "0") Integer status,  // 0-全部, 201-待付款, 301-待发货, 401-待收货, 402-已完成
                              @RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "20") Integer limit) {
    if (userId == null) {
        return ResponseUtil.unlogin();
    }
    
    List<LitemallOrder> orderList;
    int total;
    
    if (status == 0) {
        // 查询全部订单
        orderList = orderService.queryByUserId(userId, page, limit);
        total = orderService.countByUserId(userId);
    } else {
        // 查询指定状态的订单
        orderList = orderService.queryByUserIdAndStatus(userId, status.shortValue(), page, limit);
        total = orderService.countByUserIdAndStatus(userId, status.shortValue());
    }
    
    // 组装订单详情
    List<Map<String, Object>> items = new ArrayList<>();
    for (LitemallOrder order : orderList) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", order.getId());
        item.put("orderSn", order.getOrderSn());
        item.put("orderStatus", order.getOrderStatus());
        item.put("orderStatusText", OrderUtil.orderStatusText(order));
        item.put("actualPrice", order.getActualPrice());
        item.put("addTime", order.getAddTime());
        
        // 查询订单商品
        List<LitemallOrderGoods> goodsList = orderGoodsService.queryByOid(order.getId());
        List<Map<String, Object>> goodsItems = new ArrayList<>();
        for (LitemallOrderGoods goods : goodsList) {
            Map<String, Object> goodsItem = new HashMap<>();
            goodsItem.put("picUrl", goods.getPicUrl());
            goodsItem.put("goodsName", goods.getGoodsName());
            goodsItem.put("price", goods.getPrice());
            goodsItems.add(goodsItem);
        }
        item.put("goodsList", goodsItems);
        
        // 判断可执行的操作
        item.put("canCancel", OrderUtil.isCancel(order));
        item.put("canPay", OrderUtil.isPayStatus(order));
        item.put("canConfirm", OrderUtil.isConfirmStatus(order));
        item.put("canComment", OrderUtil.isCommentStatus(order));
        item.put("canAftersale", OrderUtil.isAftersaleStatus(order));
        
        items.add(item);
    }
    
    Map<String, Object> data = new HashMap<>();
    data.put("total", total);
    data.put("items", items);
    
    return ResponseUtil.ok(data);
}
```

---

### LitemallOrderService.java 扩展

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/service/LitemallOrderService.java`

```java
/**
 * 查询用户的订单列表
 */
public List<LitemallOrder> queryByUserId(Integer userId, Integer page, Integer limit) {
    LitemallOrderExample example = new LitemallOrderExample();
    example.or().andUserIdEqualTo(userId).andDeletedEqualTo(false);
    example.setOrderByClause("add_time DESC");
    
    PageHelper.startPage(page, limit);
    return orderMapper.selectByExample(example);
}

/**
 * 查询用户指定状态的订单列表
 */
public List<LitemallOrder> queryByUserIdAndStatus(Integer userId, Short status, Integer page, Integer limit) {
    LitemallOrderExample example = new LitemallOrderExample();
    example.or()
        .andUserIdEqualTo(userId)
        .andOrderStatusEqualTo(status)
        .andDeletedEqualTo(false);
    example.setOrderByClause("add_time DESC");
    
    PageHelper.startPage(page, limit);
    return orderMapper.selectByExample(example);
}

/**
 * 统计用户的订单数量
 */
public int countByUserId(Integer userId) {
    LitemallOrderExample example = new LitemallOrderExample();
    example.or().andUserIdEqualTo(userId).andDeletedEqualTo(false);
    return (int) orderMapper.countByExample(example);
}

/**
 * 统计用户指定状态的订单数量
 */
public int countByUserIdAndStatus(Integer userId, Short status) {
    LitemallOrderExample example = new LitemallOrderExample();
    example.or()
        .andUserIdEqualTo(userId)
        .andOrderStatusEqualTo(status)
        .andDeletedEqualTo(false);
    return (int) orderMapper.countByExample(example);
}

/**
 * 统计用户已完成的订单数量
 */
public int countCompletedOrders(Integer userId) {
    LitemallOrderExample example = new LitemallOrderExample();
    example.or()
        .andUserIdEqualTo(userId)
        .andOrderStatusEqualTo(OrderUtil.STATUS_CONFIRM)
        .andDeletedEqualTo(false);
    return (int) orderMapper.countByExample(example);
}
```

---

## 3. 前端代码实现

### 3.1 个人主页页面

**文件**: `litemall-wx/pages/user/user.js`

```javascript
const util = require('../../utils/util.js');
const api = require('../../config/api.js');

Page({
  data: {
    userInfo: null,
    currentTab: 0,  // 0-我的发布, 1-我的订单, 2-收藏夹
    
    // 我的发布
    goodsStatus: 1,  // 1-已上架, 0-已下架
    goodsList: [],
    goodsPage: 1,
    goodsTotal: 0,
    
    // 我的订单
    orderStatus: 0,  // 0-全部, 201-待付款, 301-待发货, 401-待收货, 402-已完成
    orderList: [],
    orderPage: 1,
    orderTotal: 0,
    
    // 收藏夹
    collectList: [],
    collectPage: 1,
    collectTotal: 0
  },

  onLoad: function() {
    this.getUserInfo();
    this.loadGoodsList();
  },

  /**
   * 获取用户信息
   */
  getUserInfo: function() {
    const that = this;
    
    util.request(api.UserInfo, {}, 'GET').then(res => {
      if (res.errno === 0) {
        that.setData({ userInfo: res.data });
      }
    });
  },

  /**
   * Tab 切换
   */
  switchTab: function(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ currentTab: tab });
    
    if (tab === 0) {
      this.loadGoodsList();
    } else if (tab === 1) {
      this.loadOrderList();
    } else if (tab === 2) {
      this.loadCollectList();
    }
  },

  /**
   * 切换商品状态（已上架/已下架）
   */
  switchGoodsStatus: function(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({
      goodsStatus: status,
      goodsList: [],
      goodsPage: 1
    });
    this.loadGoodsList();
  },

  /**
   * 加载我的发布列表
   */
  loadGoodsList: function() {
    const that = this;
    
    util.request(api.MyGoodsList, {
      status: this.data.goodsStatus,
      page: this.data.goodsPage,
      limit: 20
    }, 'GET').then(res => {
      if (res.errno === 0) {
        that.setData({
          goodsList: that.data.goodsList.concat(res.data.items),
          goodsTotal: res.data.total
        });
      }
    });
  },

  /**
   * 编辑商品
   */
  editGoods: function(e) {
    const goodsId = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: '/pages/goods/publish/publish?id=' + goodsId
    });
  },

  /**
   * 删除商品
   */
  deleteGoods: function(e) {
    const that = this;
    const goodsId = e.currentTarget.dataset.id;
    
    wx.showModal({
      title: '确认删除',
      content: '删除后无法恢复，确定要删除吗？',
      success: function(res) {
        if (res.confirm) {
          util.request(api.GoodsDelete, { id: goodsId }, 'POST').then(res => {
            if (res.errno === 0) {
              wx.showToast({
                title: '删除成功',
                icon: 'success'
              });
              
              // 刷新列表
              that.setData({
                goodsList: [],
                goodsPage: 1
              });
              that.loadGoodsList();
            } else {
              wx.showToast({
                title: res.errmsg || '删除失败',
                icon: 'none'
              });
            }
          });
        }
      }
    });
  },

  /**
   * 上架/下架商品
   */
  toggleOnSale: function(e) {
    const that = this;
    const goodsId = e.currentTarget.dataset.id;
    const isOnSale = e.currentTarget.dataset.onsale;
    
    const action = isOnSale ? '下架' : '上架';
    
    wx.showModal({
      title: '确认' + action,
      content: '确定要' + action + '此商品吗？',
      success: function(res) {
        if (res.confirm) {
          util.request(api.GoodsToggleOnSale, {
            id: goodsId,
            isOnSale: !isOnSale
          }, 'POST').then(res => {
            if (res.errno === 0) {
              wx.showToast({
                title: action + '成功',
                icon: 'success'
              });
              
              // 刷新列表
              that.setData({
                goodsList: [],
                goodsPage: 1
              });
              that.loadGoodsList();
            } else {
              wx.showToast({
                title: res.errmsg || action + '失败',
                icon: 'none'
              });
            }
          });
        }
      }
    });
  },

  /**
   * 切换订单状态
   */
  switchOrderStatus: function(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({
      orderStatus: status,
      orderList: [],
      orderPage: 1
    });
    this.loadOrderList();
  },

  /**
   * 加载我的订单列表
   */
  loadOrderList: function() {
    const that = this;
    
    util.request(api.MyOrderList, {
      status: this.data.orderStatus,
      page: this.data.orderPage,
      limit: 20
    }, 'GET').then(res => {
      if (res.errno === 0) {
        that.setData({
          orderList: that.data.orderList.concat(res.data.items),
          orderTotal: res.data.total
        });
      }
    });
  },

  /**
   * 取消订单
   */
  cancelOrder: function(e) {
    const that = this;
    const orderId = e.currentTarget.dataset.id;
    
    wx.showModal({
      title: '确认取消',
      content: '确定要取消此订单吗？',
      success: function(res) {
        if (res.confirm) {
          util.request(api.OrderCancel, { orderId: orderId }, 'POST').then(res => {
            if (res.errno === 0) {
              wx.showToast({
                title: '取消成功',
                icon: 'success'
              });
              
              // 刷新列表
              that.setData({
                orderList: [],
                orderPage: 1
              });
              that.loadOrderList();
            } else {
              wx.showToast({
                title: res.errmsg || '取消失败',
                icon: 'none'
              });
            }
          });
        }
      }
    });
  },

  /**
   * 加载收藏夹列表
   */
  loadCollectList: function() {
    const that = this;
    
    util.request(api.CollectList, {
      page: this.data.collectPage,
      limit: 20
    }, 'GET').then(res => {
      if (res.errno === 0) {
        that.setData({
          collectList: that.data.collectList.concat(res.data.items),
          collectTotal: res.data.total
        });
      }
    });
  },

  /**
   * 下拉刷新
   */
  onPullDownRefresh: function() {
    const currentTab = this.data.currentTab;
    
    if (currentTab === 0) {
      this.setData({ goodsList: [], goodsPage: 1 });
      this.loadGoodsList();
    } else if (currentTab === 1) {
      this.setData({ orderList: [], orderPage: 1 });
      this.loadOrderList();
    } else if (currentTab === 2) {
      this.setData({ collectList: [], collectPage: 1 });
      this.loadCollectList();
    }
    
    wx.stopPullDownRefresh();
  },

  /**
   * 上拉加载更多
   */
  onReachBottom: function() {
    const currentTab = this.data.currentTab;
    
    if (currentTab === 0 && this.data.goodsList.length < this.data.goodsTotal) {
      this.setData({ goodsPage: this.data.goodsPage + 1 });
      this.loadGoodsList();
    } else if (currentTab === 1 && this.data.orderList.length < this.data.orderTotal) {
      this.setData({ orderPage: this.data.orderPage + 1 });
      this.loadOrderList();
    } else if (currentTab === 2 && this.data.collectList.length < this.data.collectTotal) {
      this.setData({ collectPage: this.data.collectPage + 1 });
      this.loadCollectList();
    }
  },

  /**
   * 跳转到商品详情
   */
  goToGoodsDetail: function(e) {
    const goodsId = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: '/pages/goods/goods?id=' + goodsId
    });
  },

  /**
   * 跳转到订单详情
   */
  goToOrderDetail: function(e) {
    const orderId = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: '/pages/order/detail/detail?id=' + orderId
    });
  }
});
```

---

### 3.2 页面模板

**文件**: `litemall-wx/pages/user/user.wxml`

```xml
<view class="container">
  <!-- 用户信息区 -->
  <view class="user-header" wx:if="{{userInfo}}">
    <image class="avatar" src="{{userInfo.avatar}}" mode="aspectFill"></image>
    <view class="user-info">
      <view class="nickname">{{userInfo.nickname}}</view>
      <credit-badge userId="{{userInfo.id}}" score="{{userInfo.creditScore}}" size="medium" />
      <view class="auth-status" wx:if="{{userInfo.authStatus === 1}}">
        <text class="icon">✓</text>
        <text>学号已认证</text>
      </view>
    </view>
    <view class="user-stats">
      <view class="stat-item">
        <text class="value">{{userInfo.creditScore}}</text>
        <text class="label">积分</text>
      </view>
      <view class="stat-item">
        <text class="value">{{userInfo.orderCount}}</text>
        <text class="label">成交</text>
      </view>
    </view>
  </view>

  <!-- Tab 导航栏 -->
  <view class="tab-bar">
    <view class="tab-item {{currentTab === 0 ? 'active' : ''}}" bindtap="switchTab" data-tab="0">
      我的发布
    </view>
    <view class="tab-item {{currentTab === 1 ? 'active' : ''}}" bindtap="switchTab" data-tab="1">
      我的订单
    </view>
    <view class="tab-item {{currentTab === 2 ? 'active' : ''}}" bindtap="switchTab" data-tab="2">
      收藏夹
    </view>
  </view>

  <!-- Tab 内容区 -->
  <!-- Tab 1: 我的发布 -->
  <view class="tab-content" wx:if="{{currentTab === 0}}">
    <view class="sub-tab-bar">
      <view class="sub-tab-item {{goodsStatus === 1 ? 'active' : ''}}" 
            bindtap="switchGoodsStatus" data-status="1">
        已上架
      </view>
      <view class="sub-tab-item {{goodsStatus === 0 ? 'active' : ''}}" 
            bindtap="switchGoodsStatus" data-status="0">
        已下架
      </view>
    </view>

    <view class="goods-list">
      <view class="goods-card" wx:for="{{goodsList}}" wx:key="id">
        <image class="goods-image" src="{{item.picUrl}}" mode="aspectFill" 
               bindtap="goToGoodsDetail" data-id="{{item.id}}"></image>
        <view class="goods-info">
          <text class="goods-name">{{item.name}}</text>
          <view class="goods-meta">
            <text class="price">￥{{item.retailPrice}}</text>
            <text class="view-count">浏览：{{item.viewCount}} 次</text>
          </view>
        </view>
        <view class="goods-actions" wx:if="{{!item.isSold}}">
          <button class="btn-action" bindtap="editGoods" data-id="{{item.id}}">编辑</button>
          <button class="btn-action" bindtap="deleteGoods" data-id="{{item.id}}">删除</button>
          <button class="btn-action" bindtap="toggleOnSale" 
                  data-id="{{item.id}}" data-onsale="{{item.isOnSale}}">
            {{item.isOnSale ? '下架' : '上架'}}
          </button>
        </view>
        <view class="goods-sold" wx:else>
          <text>已售罄</text>
        </view>
      </view>
    </view>

    <view class="empty" wx:if="{{goodsList.length === 0}}">
      暂无发布
    </view>
  </view>

  <!-- Tab 2: 我的订单 -->
  <view class="tab-content" wx:if="{{currentTab === 1}}">
    <view class="sub-tab-bar">
      <view class="sub-tab-item {{orderStatus === 0 ? 'active' : ''}}" 
            bindtap="switchOrderStatus" data-status="0">全部</view>
      <view class="sub-tab-item {{orderStatus === 201 ? 'active' : ''}}" 
            bindtap="switchOrderStatus" data-status="201">待付款</view>
      <view class="sub-tab-item {{orderStatus === 301 ? 'active' : ''}}" 
            bindtap="switchOrderStatus" data-status="301">待发货</view>
      <view class="sub-tab-item {{orderStatus === 401 ? 'active' : ''}}" 
            bindtap="switchOrderStatus" data-status="401">待收货</view>
      <view class="sub-tab-item {{orderStatus === 402 ? 'active' : ''}}" 
            bindtap="switchOrderStatus" data-status="402">已完成</view>
    </view>

    <view class="order-list">
      <view class="order-card" wx:for="{{orderList}}" wx:key="id" 
            bindtap="goToOrderDetail" data-id="{{item.id}}">
        <view class="order-header">
          <text class="order-sn">订单号：{{item.orderSn}}</text>
          <text class="order-status">{{item.orderStatusText}}</text>
        </view>
        <view class="order-goods">
          <view class="goods-item" wx:for="{{item.goodsList}}" wx:for-item="goods" wx:key="index">
            <image class="goods-pic" src="{{goods.picUrl}}" mode="aspectFill"></image>
            <text class="goods-name">{{goods.goodsName}}</text>
            <text class="goods-price">￥{{goods.price}}</text>
          </view>
        </view>
        <view class="order-footer">
          <text class="total-price">总价：￥{{item.actualPrice}}</text>
          <view class="order-actions">
            <button wx:if="{{item.canCancel}}" class="btn-action" 
                    bindtap="cancelOrder" data-id="{{item.id}}" catchtap="">
              取消订单
            </button>
            <button wx:if="{{item.canPay}}" class="btn-primary">
              去付款
            </button>
          </view>
        </view>
      </view>
    </view>

    <view class="empty" wx:if="{{orderList.length === 0}}">
      暂无订单
    </view>
  </view>

  <!-- Tab 3: 收藏夹 -->
  <view class="tab-content" wx:if="{{currentTab === 2}}">
    <view class="goods-list">
      <view class="goods-card" wx:for="{{collectList}}" wx:key="id">
        <image class="goods-image" src="{{item.picUrl}}" mode="aspectFill" 
               bindtap="goToGoodsDetail" data-id="{{item.goodsId}}"></image>
        <view class="goods-info">
          <text class="goods-name">{{item.name}}</text>
          <text class="price">￥{{item.retailPrice}}</text>
          <view class="seller-info">
            <text>卖家：{{item.sellerName}}</text>
            <text class="seller-level">{{item.sellerCreditLevel}}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="empty" wx:if="{{collectList.length === 0}}">
      暂无收藏
    </view>
  </view>
</view>
```

---

### 3.3 页面样式

**文件**: `litemall-wx/pages/user/user.wxss`

```css
.container {
  background: #F5F5F5;
  min-height: 100vh;
}

/* 用户信息区 */
.user-header {
  background: #FFFFFF;
  padding: 40rpx 30rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
}

.user-info {
  flex: 1;
}

.nickname {
  font-size: 36rpx;
  font-weight: 600;
  color: #333333;
  margin-bottom: 12rpx;
}

.auth-status {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 24rpx;
  color: #52C41A;
  margin-top: 12rpx;
}

.auth-status .icon {
  font-size: 28rpx;
  font-weight: bold;
}

.user-stats {
  display: flex;
  gap: 40rpx;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.stat-item .value {
  font-size: 32rpx;
  font-weight: 600;
  color: #FF6B6B;
}

.stat-item .label {
  font-size: 24rpx;
  color: #999999;
}

/* Tab 导航栏 */
.tab-bar {
  background: #FFFFFF;
  display: flex;
  margin-top: 20rpx;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 30rpx 0;
  font-size: 28rpx;
  color: #666666;
  border-bottom: 4rpx solid transparent;
}

.tab-item.active {
  color: #FF6B6B;
  font-weight: 600;
  border-bottom-color: #FF6B6B;
}

/* 子 Tab */
.sub-tab-bar {
  background: #FFFFFF;
  display: flex;
  padding: 20rpx 30rpx;
  gap: 30rpx;
}

.sub-tab-item {
  padding: 12rpx 24rpx;
  font-size: 26rpx;
  color: #666666;
  border-radius: 8rpx;
}

.sub-tab-item.active {
  color: #FF6B6B;
  background: #FFE5E5;
}

/* 商品列表 */
.goods-list {
  padding: 20rpx;
}

.goods-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  margin-bottom: 20rpx;
  padding: 20rpx;
}

.goods-image {
  width: 100%;
  height: 400rpx;
  border-radius: 12rpx;
}

.goods-info {
  margin-top: 16rpx;
}

.goods-name {
  font-size: 28rpx;
  color: #333333;
  display: block;
  margin-bottom: 12rpx;
}

.goods-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.price {
  font-size: 32rpx;
  color: #FF6B6B;
  font-weight: 600;
}

.view-count {
  font-size: 24rpx;
  color: #999999;
}

.goods-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 20rpx;
}

.btn-action {
  flex: 1;
  height: 60rpx;
  line-height: 60rpx;
  background: #F5F5F5;
  color: #666666;
  font-size: 26rpx;
  border-radius: 8rpx;
}

.goods-sold {
  text-align: center;
  padding: 20rpx;
  color: #999999;
  font-size: 26rpx;
}

/* 订单列表 */
.order-list {
  padding: 20rpx;
}

.order-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  margin-bottom: 20rpx;
  padding: 20rpx;
}

.order-header {
  display: flex;
  justify-content: space-between;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #EEEEEE;
}

.order-sn {
  font-size: 24rpx;
  color: #999999;
}

.order-status {
  font-size: 26rpx;
  color: #FF6B6B;
  font-weight: 600;
}

.order-goods {
  padding: 20rpx 0;
}

.goods-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.goods-pic {
  width: 120rpx;
  height: 120rpx;
  border-radius: 8rpx;
}

.goods-item .goods-name {
  flex: 1;
  font-size: 26rpx;
  color: #333333;
}

.goods-item .goods-price {
  font-size: 28rpx;
  color: #FF6B6B;
}

.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16rpx;
  border-top: 1rpx solid #EEEEEE;
}

.total-price {
  font-size: 28rpx;
  color: #333333;
  font-weight: 600;
}

.order-actions {
  display: flex;
  gap: 20rpx;
}

.btn-primary {
  padding: 12rpx 24rpx;
  background: #FF6B6B;
  color: #FFFFFF;
  font-size: 26rpx;
  border-radius: 8rpx;
}

/* 空状态 */
.empty {
  text-align: center;
  padding: 100rpx 0;
  color: #999999;
  font-size: 28rpx;
}
```

---

### 3.4 页面配置

**文件**: `litemall-wx/pages/user/user.json`

```json
{
  "navigationBarTitleText": "个人中心",
  "enablePullDownRefresh": true,
  "usingComponents": {
    "credit-badge": "/components/credit-badge/credit-badge"
  }
}
```

---

## 4. API 配置

**文件**: `litemall-wx/config/api.js`

```javascript
module.exports = {
  UserInfo: WxApiRoot + 'user/info',
  MyGoodsList: WxApiRoot + 'goods/myList',
  GoodsToggleOnSale: WxApiRoot + 'goods/toggleOnSale',
  GoodsDelete: WxApiRoot + 'goods/delete',
  MyOrderList: WxApiRoot + 'order/myList',
  OrderCancel: WxApiRoot + 'order/cancel',
  CollectList: WxApiRoot + 'collect/list',
  // ...
};
```

---

## 5. 测试指南

### 5.1 单元测试

**测试用例**: 查询我的发布列表

```java
@Test
public void testGetMyGoodsList_Success() throws Exception {
    mockMvc.perform(get("/wx/goods/myList")
            .param("status", "1")
            .param("page", "1")
            .param("limit", "20")
            .header("X-Litemall-Token", "test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errno").value(0))
        .andExpect(jsonPath("$.data.items").isArray());
}
```

---

## 6. 常见问题

### Q1: 如何区分买家订单和卖家订单？
**A**: 当前接口返回的是买家订单（用户购买的商品）。如需卖家订单（用户出售的商品），需新增接口查询 `litemall_order` 表中 `seller_id` 字段。

### Q2: 商品编辑后是否需要重新审核？
**A**: 建议价格变动超过 30% 时触发重新审核，防止恶意修改价格。

---

**状态**: 开发上下文已生成，等待开发人员实现  
**预计完成时间**: 8 小时  
**下一步**: Epic 1 所有故事准备完成，可开始编码实现
