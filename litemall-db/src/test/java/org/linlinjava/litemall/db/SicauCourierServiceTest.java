package org.linlinjava.litemall.db;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.SicauCourier;
import org.linlinjava.litemall.db.domain.SicauStudentAuth;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.linlinjava.litemall.db.service.SicauCourierService;
import org.linlinjava.litemall.db.service.SicauStudentAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 快递员服务测试类
 * 测试 Story 4.1 所有验收标准
 */
@WebAppConfiguration
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional  // 每个测试方法执行后回滚
public class SicauCourierServiceTest {

    @Autowired
    private SicauCourierService courierService;

    @Autowired
    private LitemallUserService userService;

    @Autowired
    private SicauStudentAuthService studentAuthService;

    /**
     * AC1: 资格验证 - 申请成功
     * Given 用户已完成学号认证且信用等级 ≥ 70 分
     * When 用户提交快递员申请
     * Then 申请成功提交，状态为"待审核"
     */
    @Test
    public void testApplySuccess() {
        // 1. 创建测试用户（信用分 85）
        LitemallUser user = new LitemallUser();
        user.setUsername("test_courier_user");
        user.setPassword("123456");
        user.setMobile("13800138001");
        user.setCreditScore(85);
        userService.add(user);
        Integer userId = user.getId();

        // 2. 创建学号认证记录（已认证）
        SicauStudentAuth auth = new SicauStudentAuth();
        auth.setUserId(userId);
        auth.setStudentNo("2020123456"); // 实际应加密
        auth.setRealName("测试用户");
        auth.setCampus("雅安本部");
        auth.setCollege("信息工程学院");
        auth.setStatus((byte) 2); // 已认证
        studentAuthService.add(auth);

        // 3. 申请快递员
        String applyReason = "我是大三学生，课余时间充足，想通过配送赚取生活费";
        SicauCourier courier = courierService.apply(userId, applyReason);

        // 4. 验证结果
        Assert.assertNotNull("快递员记录应创建成功", courier);
        Assert.assertNotNull("快递员ID应自动生成", courier.getId());
        Assert.assertEquals("用户ID应匹配", userId, courier.getUserId());
        Assert.assertEquals("状态应为待审核", Byte.valueOf((byte) 0), courier.getStatus());
        Assert.assertEquals("申请理由应保存", applyReason, courier.getApplyReason());
        Assert.assertEquals("初始订单数应为0", Integer.valueOf(0), courier.getTotalOrders());
        Assert.assertEquals("初始收入应为0", BigDecimal.ZERO, courier.getTotalIncome());
        Assert.assertNotNull("申请时间应自动设置", courier.getApplyTime());
    }

    /**
     * AC2: 资格不足拒绝 - 信用分不足
     * Given 用户信用等级 < 70 分
     * When 用户尝试申请快递员
     * Then 显示错误提示"信用等级不足 ⭐⭐（良好），无法申请"
     */
    @Test
    public void testApplyInsufficientCredit() {
        // 1. 创建测试用户（信用分 65，不足70）
        LitemallUser user = new LitemallUser();
        user.setUsername("test_low_credit");
        user.setPassword("123456");
        user.setMobile("13800138002");
        user.setCreditScore(65);
        userService.add(user);
        Integer userId = user.getId();

        // 2. 创建学号认证记录（已认证）
        SicauStudentAuth auth = new SicauStudentAuth();
        auth.setUserId(userId);
        auth.setStudentNo("2020123457");
        auth.setRealName("低分用户");
        auth.setCampus("雅安本部");
        auth.setCollege("信息工程学院");
        auth.setStatus((byte) 2); // 已认证
        studentAuthService.add(auth);

        // 3. 尝试申请快递员
        try {
            courierService.apply(userId, "我想成为快递员");
            Assert.fail("应抛出信用分不足异常");
        } catch (RuntimeException e) {
            Assert.assertTrue("错误信息应包含'信用等级不足'",
                    e.getMessage().contains("信用等级不足"));
        }
    }

    /**
     * AC3: 未认证拒绝
     * Given 用户未完成学号认证
     * When 用户尝试申请快递员
     * Then 显示错误提示"请先完成学号认证"
     */
    @Test
    public void testApplyNotAuthenticated() {
        // 1. 创建测试用户（信用分足够但未认证）
        LitemallUser user = new LitemallUser();
        user.setUsername("test_not_auth");
        user.setPassword("123456");
        user.setMobile("13800138003");
        user.setCreditScore(80);
        userService.add(user);
        Integer userId = user.getId();

        // 2. 不创建学号认证记录（未认证）

        // 3. 尝试申请快递员
        try {
            courierService.apply(userId, "我想成为快递员");
            Assert.fail("应抛出未认证异常");
        } catch (RuntimeException e) {
            Assert.assertTrue("错误信息应包含'请先完成学号认证'",
                    e.getMessage().contains("请先完成学号认证"));
        }
    }

    /**
     * AC4: 重复申请拒绝
     * Given 用户已申请过快递员
     * When 用户再次尝试申请
     * Then 显示错误提示"您已申请过快递员"
     */
    @Test
    public void testApplyDuplicate() {
        // 1. 创建测试用户
        LitemallUser user = new LitemallUser();
        user.setUsername("test_duplicate");
        user.setPassword("123456");
        user.setMobile("13800138004");
        user.setCreditScore(80);
        userService.add(user);
        Integer userId = user.getId();

        // 2. 创建学号认证记录
        SicauStudentAuth auth = new SicauStudentAuth();
        auth.setUserId(userId);
        auth.setStudentNo("2020123458");
        auth.setRealName("重复申请用户");
        auth.setCampus("雅安本部");
        auth.setCollege("信息工程学院");
        auth.setStatus((byte) 2);
        studentAuthService.add(auth);

        // 3. 第一次申请成功
        SicauCourier courier1 = courierService.apply(userId, "第一次申请");
        Assert.assertNotNull("第一次申请应成功", courier1);

        // 4. 第二次申请应失败
        try {
            courierService.apply(userId, "第二次申请");
            Assert.fail("应抛出重复申请异常");
        } catch (RuntimeException e) {
            Assert.assertTrue("错误信息应包含'已申请过快递员'",
                    e.getMessage().contains("已申请过快递员"));
        }
    }

    /**
     * AC5: 取消资格用户拒绝
     * Given 用户快递员资格已被取消（status=3）
     * When 用户尝试重新申请
     * Then 显示错误提示"您的快递员资格已被取消，无法重新申请"
     */
    @Test
    public void testApplyCancelled() {
        // 1. 创建测试用户
        LitemallUser user = new LitemallUser();
        user.setUsername("test_cancelled");
        user.setPassword("123456");
        user.setMobile("13800138005");
        user.setCreditScore(80);
        userService.add(user);
        Integer userId = user.getId();

        // 2. 创建学号认证记录
        SicauStudentAuth auth = new SicauStudentAuth();
        auth.setUserId(userId);
        auth.setStudentNo("2020123459");
        auth.setRealName("取消资格用户");
        auth.setCampus("雅安本部");
        auth.setCollege("信息工程学院");
        auth.setStatus((byte) 2);
        studentAuthService.add(auth);

        // 3. 先申请成功
        SicauCourier courier = courierService.apply(userId, "初次申请");
        Assert.assertNotNull(courier);

        // 4. 审核通过
        courierService.reviewApplication(courier.getId(), true, null);

        // 5. 取消资格
        courierService.cancelQualification(userId, "超时3次");
        
        // 6. 验证状态已变为取消资格
        SicauCourier cancelledCourier = courierService.findByUserId(userId);
        Assert.assertEquals("状态应为已取消资格", Byte.valueOf((byte) 3), cancelledCourier.getStatus());

        // 7. 尝试重新申请
        try {
            courierService.apply(userId, "重新申请");
            Assert.fail("应抛出资格已取消异常");
        } catch (RuntimeException e) {
            Assert.assertTrue("错误信息应包含'资格已被取消'",
                    e.getMessage().contains("资格已被取消"));
        }
    }

    /**
     * 测试: 查询快递员信息
     */
    @Test
    public void testFindByUserId() {
        // 1. 创建测试数据
        LitemallUser user = new LitemallUser();
        user.setUsername("test_find");
        user.setPassword("123456");
        user.setMobile("13800138006");
        user.setCreditScore(80);
        userService.add(user);

        SicauStudentAuth auth = new SicauStudentAuth();
        auth.setUserId(user.getId());
        auth.setStudentNo("2020123460");
        auth.setRealName("查询测试");
        auth.setCampus("雅安本部");
        auth.setCollege("信息工程学院");
        auth.setStatus((byte) 2);
        studentAuthService.add(auth);

        // 2. 申请快递员
        courierService.apply(user.getId(), "测试查询");

        // 3. 查询快递员信息
        SicauCourier found = courierService.findByUserId(user.getId());
        Assert.assertNotNull("应能查询到快递员信息", found);
        Assert.assertEquals("用户ID应匹配", user.getId(), found.getUserId());
    }

    /**
     * 测试: 审核快递员申请
     */
    @Test
    public void testReviewApplication() {
        // 1. 创建测试数据
        LitemallUser user = new LitemallUser();
        user.setUsername("test_review");
        user.setPassword("123456");
        user.setMobile("13800138007");
        user.setCreditScore(80);
        userService.add(user);

        SicauStudentAuth auth = new SicauStudentAuth();
        auth.setUserId(user.getId());
        auth.setStudentNo("2020123461");
        auth.setRealName("审核测试");
        auth.setCampus("雅安本部");
        auth.setCollege("信息工程学院");
        auth.setStatus((byte) 2);
        studentAuthService.add(auth);

        SicauCourier courier = courierService.apply(user.getId(), "测试审核");

        // 2. 审核通过
        courierService.reviewApplication(courier.getId(), true, null);
        SicauCourier approved = courierService.findByUserId(user.getId());
        Assert.assertEquals("审核通过后状态应为1", Byte.valueOf((byte) 1), approved.getStatus());
        Assert.assertNotNull("审核时间应设置", approved.getApproveTime());

        // 3. 创建另一个用户测试审核拒绝
        LitemallUser user2 = new LitemallUser();
        user2.setUsername("test_reject");
        user2.setPassword("123456");
        user2.setMobile("13800138008");
        user2.setCreditScore(80);
        userService.add(user2);

        SicauStudentAuth auth2 = new SicauStudentAuth();
        auth2.setUserId(user2.getId());
        auth2.setStudentNo("2020123462");
        auth2.setRealName("拒绝测试");
        auth2.setCampus("雅安本部");
        auth2.setCollege("信息工程学院");
        auth2.setStatus((byte) 2);
        studentAuthService.add(auth2);

        SicauCourier courier2 = courierService.apply(user2.getId(), "测试拒绝");

        // 4. 审核拒绝
        String rejectReason = "信息不完整";
        courierService.reviewApplication(courier2.getId(), false, rejectReason);
        SicauCourier rejected = courierService.findByUserId(user2.getId());
        Assert.assertEquals("审核拒绝后状态应为2", Byte.valueOf((byte) 2), rejected.getStatus());
        Assert.assertEquals("拒绝理由应保存", rejectReason, rejected.getRejectReason());
    }

    /**
     * 测试: 检查是否是已认证的快递员
     */
    @Test
    public void testIsApprovedCourier() {
        // 1. 创建测试数据
        LitemallUser user = new LitemallUser();
        user.setUsername("test_approved");
        user.setPassword("123456");
        user.setMobile("13800138009");
        user.setCreditScore(80);
        userService.add(user);

        SicauStudentAuth auth = new SicauStudentAuth();
        auth.setUserId(user.getId());
        auth.setStudentNo("2020123463");
        auth.setRealName("认证测试");
        auth.setCampus("雅安本部");
        auth.setCollege("信息工程学院");
        auth.setStatus((byte) 2);
        studentAuthService.add(auth);

        // 2. 未申请时应返回 false
        Assert.assertFalse("未申请时应返回false", courierService.isApprovedCourier(user.getId()));

        // 3. 申请后待审核时应返回 false
        SicauCourier courier = courierService.apply(user.getId(), "测试");
        Assert.assertFalse("待审核时应返回false", courierService.isApprovedCourier(user.getId()));

        // 4. 审核通过后应返回 true
        courierService.reviewApplication(courier.getId(), true, null);
        Assert.assertTrue("审核通过后应返回true", courierService.isApprovedCourier(user.getId()));
    }
}
