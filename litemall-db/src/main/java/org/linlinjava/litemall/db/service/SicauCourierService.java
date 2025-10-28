package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.util.BuildingCoordinates;
import org.linlinjava.litemall.db.util.DistanceCalculator;
import org.linlinjava.litemall.db.dao.SicauCourierMapper;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.LitemallOrderExample;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.SicauCourier;
import org.linlinjava.litemall.db.domain.SicauCourierExample;
import org.linlinjava.litemall.db.domain.SicauStudentAuth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 快递员服务类
 * 管理学生快递员的申请、审核、资格管理等功能
 */
@Service
public class SicauCourierService {
    
    @Resource
    private SicauCourierMapper courierMapper;
    
    @Autowired
    private SicauStudentAuthService studentAuthService;
    
    @Autowired
    private LitemallUserService userService;
    
    @Autowired
    private LitemallOrderService orderService;
    
    @Autowired
    private SicauCourierIncomeService incomeService;

    /**
     * 根据主键查询快递员信息
     * @param id 主键ID
     * @return 快递员信息
     */
    public SicauCourier findById(Integer id) {
        return courierMapper.selectByPrimaryKey(id);
    }

    /**
     * 根据用户ID查询快递员信息
     * @param userId 用户ID
     * @return 快递员信息
     */
    public SicauCourier findByUserId(Integer userId) {
        SicauCourierExample example = new SicauCourierExample();
        example.or().andUserIdEqualTo(userId).andDeletedEqualTo(false);
        List<SicauCourier> list = courierMapper.selectByExample(example);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 根据状态查询快递员列表
     * @param status 快递员状态：0-待审核, 1-已通过, 2-已拒绝, 3-已取消资格
     * @return 快递员列表
     */
    public List<SicauCourier> listByStatus(Byte status) {
        SicauCourierExample example = new SicauCourierExample();
        example.or().andStatusEqualTo(status).andDeletedEqualTo(false);
        example.setOrderByClause("apply_time DESC");
        return courierMapper.selectByExample(example);
    }

    /**
     * 申请成为快递员
     * @param userId 用户ID
     * @param applyReason 申请理由
     * @return 是否申请成功
     * @throws RuntimeException 当不符合申请条件时抛出异常
     */
    @Transactional
    public SicauCourier apply(Integer userId, String applyReason) {
        // 1. 检查是否已申请
        SicauCourier existCourier = findByUserId(userId);
        if (existCourier != null) {
            if (existCourier.getStatus() == 3) {
                throw new RuntimeException("您的快递员资格已被取消，无法重新申请");
            }
            throw new RuntimeException("您已申请过快递员");
        }
        
        // 2. 检查学号认证
        SicauStudentAuth studentAuth = studentAuthService.findByUserId(userId);
        if (studentAuth == null || studentAuth.getStatus() != 2) {
            throw new RuntimeException("请先完成学号认证");
        }
        
        // 3. 检查信用等级（≥ 70 分）
        LitemallUser user = userService.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        Integer creditScore = user.getCreditScore();
        if (creditScore == null || creditScore < 70) {
            throw new RuntimeException("信用等级不足 ⭐⭐（良好），无法申请");
        }
        
        // 4. 创建快递员申请
        SicauCourier courier = new SicauCourier();
        courier.setUserId(userId);
        courier.setStatus((byte) 0); // 待审核
        courier.setApplyReason(applyReason);
        courier.setTotalOrders(0);
        courier.setTotalIncome(java.math.BigDecimal.ZERO);
        courier.setTimeoutCount(0);
        courier.setComplaintCount(0);
        courier.setApplyTime(LocalDateTime.now());
        courier.setAddTime(LocalDateTime.now());
        courier.setUpdateTime(LocalDateTime.now());
        courier.setDeleted(false);
        
        courierMapper.insertSelective(courier);
        
        return courier;
    }

    /**
     * 审核快递员申请
     * @param id 快递员申请ID
     * @param approved 是否通过
     * @param rejectReason 拒绝理由（审核不通过时必填）
     * @return 影响行数
     */
    @Transactional
    public int reviewApplication(Integer id, boolean approved, String rejectReason) {
        SicauCourier courier = findById(id);
        if (courier == null) {
            throw new RuntimeException("快递员申请不存在");
        }
        
        if (courier.getStatus() != 0) {
            throw new RuntimeException("该申请已被审核");
        }
        
        courier.setStatus(approved ? (byte) 1 : (byte) 2);
        if (approved) {
            courier.setApproveTime(LocalDateTime.now());
        } else {
            if (rejectReason == null || rejectReason.trim().isEmpty()) {
                throw new RuntimeException("拒绝时必须填写拒绝理由");
            }
            courier.setRejectReason(rejectReason);
        }
        courier.setUpdateTime(LocalDateTime.now());
        
        return courierMapper.updateByPrimaryKeySelective(courier);
    }

    /**
     * 取消快递员资格
     * @param userId 用户ID
     * @param reason 取消原因
     * @return 影响行数
     */
    @Transactional
    public int cancelQualification(Integer userId, String reason) {
        SicauCourier courier = findByUserId(userId);
        if (courier == null) {
            throw new RuntimeException("该用户不是快递员");
        }
        
        if (courier.getStatus() != 1) {
            throw new RuntimeException("快递员状态异常");
        }
        
        courier.setStatus((byte) 3); // 已取消资格
        courier.setRejectReason(reason); // 复用 reject_reason 字段存储取消原因
        courier.setUpdateTime(LocalDateTime.now());
        
        return courierMapper.updateByPrimaryKeySelective(courier);
    }

    /**
     * 更新快递员信息
     * @param courier 快递员信息
     * @return 影响行数
     */
    public int updateById(SicauCourier courier) {
        courier.setUpdateTime(LocalDateTime.now());
        return courierMapper.updateByPrimaryKeySelective(courier);
    }

    /**
     * 增加配送订单数和收入
     * @param userId 快递员用户ID
     * @param income 配送费收入
     */
    @Transactional
    public void addDeliveryRecord(Integer userId, java.math.BigDecimal income) {
        SicauCourier courier = findByUserId(userId);
        if (courier == null || courier.getStatus() != 1) {
            throw new RuntimeException("快递员状态异常");
        }
        
        courier.setTotalOrders(courier.getTotalOrders() + 1);
        courier.setTotalIncome(courier.getTotalIncome().add(income));
        updateById(courier);
    }

    /**
     * 增加超时次数
     * @param userId 快递员用户ID
     * @return 当前超时次数
     */
    @Transactional
    public int incrementTimeoutCount(Integer userId) {
        SicauCourier courier = findByUserId(userId);
        if (courier == null) {
            throw new RuntimeException("快递员不存在");
        }
        
        courier.setTimeoutCount(courier.getTimeoutCount() + 1);
        updateById(courier);
        
        return courier.getTimeoutCount();
    }

    /**
     * 增加投诉次数
     * @param userId 快递员用户ID
     * @return 当前投诉次数
     */
    @Transactional
    public int incrementComplaintCount(Integer userId) {
        SicauCourier courier = findByUserId(userId);
        if (courier == null) {
            throw new RuntimeException("快递员不存在");
        }
        
        courier.setComplaintCount(courier.getComplaintCount() + 1);
        updateById(courier);
        
        return courier.getComplaintCount();
    }

    /**
     * 检查用户是否是已认证的快递员
     * @param userId 用户ID
     * @return 是否是已认证的快递员
     */
    public boolean isApprovedCourier(Integer userId) {
        SicauCourier courier = findByUserId(userId);
        return courier != null && courier.getStatus() == 1;
    }

    /**
     * 删除快递员记录（逻辑删除）
     * @param id 主键ID
     * @return 影响行数
     */
    public int deleteById(Integer id) {
        SicauCourier courier = new SicauCourier();
        courier.setId(id);
        courier.setDeleted(true);
        courier.setUpdateTime(LocalDateTime.now());
        return courierMapper.updateByPrimaryKeySelective(courier);
    }

    /**
     * 查询待配送订单列表（Story 4.2）
     * @param courierId 快递员用户ID
     * @return 待配送订单列表（含距离和配送费）
     */
    public List<Map<String, Object>> queryPendingOrders(Integer courierId) {
        // 1. 检查快递员资格
        SicauCourier courier = findByUserId(courierId);
        if (courier == null) {
            throw new RuntimeException("您不是快递员");
        }
        if (courier.getStatus() != 1) {
            throw new RuntimeException("您的快递员资格尚未通过审核或已被取消");
        }

        // 2. 查询待配送订单（状态=201待发货，配送方式=1快递员配送，未分配快递员）
        LitemallOrderExample example = new LitemallOrderExample();
        example.or()
            .andOrderStatusEqualTo((short) 201)      // 待发货
            .andDeliveryTypeEqualTo((byte) 1)         // 学生快递员配送
            .andCourierIdIsNull()                     // 未分配快递员
            .andDeletedEqualTo(false);
        
        // 使用 querySelective 查询
        List<LitemallOrder> orders = orderService.querySelective(
            null, null, null, null, 
            java.util.Arrays.asList((short) 201), 
            1, 1000, null, null
        );

        // 手动过滤（因为 querySelective 不支持所有条件）
        List<LitemallOrder> filteredOrders = new ArrayList<>();
        for (LitemallOrder order : orders) {
            if (order.getDeliveryType() != null && order.getDeliveryType() == 1 
                && order.getCourierId() == null) {
                filteredOrders.add(order);
            }
        }

        // 3. 计算距离和配送费
        // TODO: 获取快递员位置（从默认收货地址或其他来源）
        // 当前简化实现：假设快递员在校内，距离固定为0作为起点
        double[] courierCoords = null; // 暂时无快递员位置

        List<Map<String, Object>> result = new ArrayList<>();
        for (LitemallOrder order : filteredOrders) {
            // 3.1 从订单地址中提取楼栋名称
            String buildingName = DistanceCalculator.extractBuildingName(order.getAddress());
            if (buildingName == null) {
                continue; // 无法提取楼栋，跳过
            }

            // 获取目标楼栋坐标
            double[] targetCoords = BuildingCoordinates.getCoordinates(buildingName);
            if (targetCoords == null) {
                continue; // 楼栋坐标未找到，跳过
            }

            // 3.2 计算距离（如果没有快递员坐标，默认为校内平均距离1.5km）
            double distance = 1.5; // 默认距离
            if (courierCoords != null) {
                distance = DistanceCalculator.calculateDistance(
                    courierCoords[0], courierCoords[1],
                    targetCoords[0], targetCoords[1]
                );
            }

            // 3.3 计算配送费
            double fee = DistanceCalculator.calculateFee(distance);

            // 3.4 构造返回数据
            Map<String, Object> item = new HashMap<>();
            item.put("orderId", order.getId());
            item.put("orderSn", order.getOrderSn());
            item.put("consignee", order.getConsignee());
            
            // 手机号脱敏：138****8000
            String mobile = order.getMobile();
            if (mobile != null && mobile.length() == 11) {
                mobile = mobile.substring(0, 3) + "****" + mobile.substring(7);
            }
            item.put("mobile", mobile);
            
            item.put("address", order.getAddress());
            item.put("buildingName", buildingName);
            item.put("distance", distance);
            item.put("fee", fee);
            item.put("actualPrice", order.getActualPrice());
            item.put("addTime", order.getAddTime());
            
            result.add(item);
        }

        // 4. 按距离升序排序（最近的订单在前）
        result.sort((a, b) -> {
            Double distA = (Double) a.get("distance");
            Double distB = (Double) b.get("distance");
            return Double.compare(distA, distB);
        });

        return result;
    }

    /**
     * 接单（Story 4.3）
     * @param courierId 快递员用户ID
     * @param orderId 订单ID
     * @return 订单详情（含取件码）
     */
    @Transactional
    public Map<String, Object> acceptOrder(Integer courierId, Integer orderId) {
        // 1. 检查快递员资格
        SicauCourier courier = findByUserId(courierId);
        if (courier == null) {
            throw new RuntimeException("您不是快递员");
        }
        if (courier.getStatus() != 1) {
            throw new RuntimeException("您的快递员资格未通过审核或已被取消");
        }

        // 2. 查询订单
        LitemallOrder order = orderService.findById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        // 3. 检查订单状态
        if (order.getOrderStatus() != 201) {
            throw new RuntimeException("订单状态不正确（必须是待发货）");
        }
        if (order.getDeliveryType() != 1) {
            throw new RuntimeException("该订单不是学生快递员配送方式");
        }
        if (order.getCourierId() != null) {
            throw new RuntimeException("该订单已被其他快递员接取");
        }

        // 4. 生成4位取件码
        String pickupCode = String.format("%04d", (int)(Math.random() * 10000));

        // 5. 更新订单
        order.setCourierId(courierId);
        order.setOrderStatus((short) 301); // 待收货
        order.setShipTime(LocalDateTime.now());
        order.setPickupCode(pickupCode);
        
        int updated = orderService.updateWithOptimisticLocker(order);
        if (updated == 0) {
            throw new RuntimeException("订单已被其他快递员接取");
        }

        // 6. TODO: 发送通知给买家（包含取件码）
        // notifyService.notifyBuyer(order.getUserId(), "您的订单已由快递员接单，取件码：" + pickupCode);

        // 7. 构造返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("pickupCode", pickupCode);
        result.put("orderSn", order.getOrderSn());
        result.put("consignee", order.getConsignee());
        
        // 手机号脱敏
        String mobile = order.getMobile();
        if (mobile != null && mobile.length() == 11) {
            mobile = mobile.substring(0, 3) + "****" + mobile.substring(7);
        }
        result.put("mobile", mobile);
        
        result.put("address", order.getAddress());
        result.put("shipTime", order.getShipTime());

        return result;
    }

    /**
     * 完成配送（Story 4.3）
     * @param courierId 快递员用户ID
     * @param orderId 订单ID
     * @param pickupCode 买家提供的取件码
     * @return 收入信息
     */
    @Transactional
    public Map<String, Object> completeOrder(Integer courierId, Integer orderId, String pickupCode) {
        // 1. 查询订单
        LitemallOrder order = orderService.findById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        // 2. 检查订单归属
        if (order.getCourierId() == null || !order.getCourierId().equals(courierId)) {
            throw new RuntimeException("这不是您接的订单");
        }

        // 3. 检查订单状态
        if (order.getOrderStatus() != 301) {
            throw new RuntimeException("订单状态不正确（必须是待收货）");
        }

        // 4. 验证取件码
        if (order.getPickupCode() == null || !order.getPickupCode().equals(pickupCode)) {
            throw new RuntimeException("取件码错误");
        }

        // 5. 计算配送距离和费用
        String buildingName = DistanceCalculator.extractBuildingName(order.getAddress());
        double[] coords = BuildingCoordinates.getCoordinates(buildingName);
        
        // 默认距离1.5km（如果无法计算）
        double distance = 1.5;
        if (coords != null) {
            // TODO: 获取快递员起点坐标后可精确计算
            // 暂时使用默认距离
        }
        
        double fee = DistanceCalculator.calculateFee(distance);

        // 6. 更新订单状态
        order.setOrderStatus((short) 401); // 已收货
        order.setConfirmTime(LocalDateTime.now());
        orderService.updateWithOptimisticLocker(order);

        // 7. 记录收入流水
        incomeService.addIncome(
            courierId, 
            orderId, 
            java.math.BigDecimal.valueOf(fee), 
            java.math.BigDecimal.valueOf(distance)
        );

        // 8. 更新快递员统计
        SicauCourier courier = findByUserId(courierId);
        courier.setTotalOrders(courier.getTotalOrders() + 1);
        courier.setTotalIncome(
            courier.getTotalIncome().add(java.math.BigDecimal.valueOf(fee))
        );
        courier.setUpdateTime(LocalDateTime.now());
        updateById(courier);

        // 9. TODO: 通知买家确认收货
        // notifyService.notifyBuyer(order.getUserId(), "您的订单已送达，感谢使用学生快递员服务");

        // 10. 构造返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("income", fee);
        result.put("distance", distance);
        result.put("orderSn", order.getOrderSn());
        result.put("totalOrders", courier.getTotalOrders());
        result.put("totalIncome", courier.getTotalIncome());

        return result;
    }
}
