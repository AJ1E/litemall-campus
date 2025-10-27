package org.linlinjava.litemall.admin.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.linlinjava.litemall.admin.annotation.RequiresPermissionsDesc;
import org.linlinjava.litemall.core.util.AesUtil;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.service.CreditScoreService;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.SicauStudentAuth;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.linlinjava.litemall.db.service.SicauStudentAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

@RestController
@RequestMapping("/admin/user")
@Validated
public class AdminUserController {
    private final Log logger = LogFactory.getLog(AdminUserController.class);

    @Autowired
    private LitemallUserService userService;

    @Autowired
    private SicauStudentAuthService studentAuthService;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private CreditScoreService creditScoreService;

    @RequiresPermissions("admin:user:list")
    @RequiresPermissionsDesc(menu = {"用户管理", "会员管理"}, button = "查询")
    @GetMapping("/list")
    public Object list(String username, String mobile,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        List<LitemallUser> userList = userService.querySelective(username, mobile, page, limit, sort, order);
        return ResponseUtil.okList(userList);
    }
    @RequiresPermissions("admin:user:list")
    @RequiresPermissionsDesc(menu = {"用户管理", "会员管理"}, button = "详情")
    @GetMapping("/detail")
    public Object userDetail(@NotNull Integer id) {
    	LitemallUser user=userService.findById(id);
        return ResponseUtil.ok(user);
    }
    @RequiresPermissions("admin:user:list")
    @RequiresPermissionsDesc(menu = {"用户管理", "会员管理"}, button = "编辑")
    @PostMapping("/update")
    public Object userUpdate(@RequestBody LitemallUser user) {
        return ResponseUtil.ok(userService.updateById(user));
    }

    /**
     * 查询待审核的学号认证列表
     * 
     * @param page 页码
     * @param limit 每页数量
     * @return 待审核认证列表
     */
    @RequiresPermissions("admin:user:list")
    @RequiresPermissionsDesc(menu = {"用户管理", "学号认证审核"}, button = "查询")
    @GetMapping("/listPendingAuths")
    public Object listPendingAuths(@RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer limit) {
        List<SicauStudentAuth> authList = studentAuthService.listByStatus((byte) 1);
        
        // 解密敏感信息供管理员审核
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (SicauStudentAuth auth : authList) {
            try {
                Map<String, Object> item = new HashMap<>();
                item.put("id", auth.getId());
                item.put("userId", auth.getUserId());
                item.put("studentNo", aesUtil.decrypt(auth.getStudentNo()));
                item.put("realName", aesUtil.decrypt(auth.getRealName()));
                item.put("college", auth.getCollege());
                item.put("major", auth.getMajor());
                item.put("studentCardUrl", auth.getStudentCardUrl());
                item.put("submitTime", auth.getSubmitTime());
                
                // 获取用户信息
                LitemallUser user = userService.findById(auth.getUserId());
                if (user != null) {
                    item.put("nickname", user.getNickname());
                    item.put("avatar", user.getAvatar());
                }
                
                resultList.add(item);
            } catch (Exception e) {
                logger.error("解密学号认证信息失败", e);
            }
        }
        
        return ResponseUtil.ok(resultList);
    }

    /**
     * 审核学号认证
     * 
     * @param body 包含 id, status (2-通过, 3-拒绝), reason (拒绝原因)
     * @return 审核结果
     */
    @RequiresPermissions("admin:user:list")
    @RequiresPermissionsDesc(menu = {"用户管理", "学号认证审核"}, button = "审核")
    @PostMapping("/auditAuth")
    public Object auditAuth(@RequestBody String body) {
        Integer id = JacksonUtil.parseInteger(body, "id");
        Integer status = JacksonUtil.parseInteger(body, "status");
        String reason = JacksonUtil.parseString(body, "reason");

        if (id == null || status == null) {
            return ResponseUtil.badArgument();
        }

        if (status != 2 && status != 3) {
            return ResponseUtil.fail(402, "审核状态错误");
        }

        if (status == 3 && StringUtils.isEmpty(reason)) {
            return ResponseUtil.fail(402, "拒绝时必须填写原因");
        }

        // 获取当前管理员信息
        Subject currentUser = SecurityUtils.getSubject();
        LitemallAdmin admin = (LitemallAdmin) currentUser.getPrincipal();

        try {
            if (status == 2) {
                // 通过审核
                SicauStudentAuth auth = studentAuthService.findById(id);
                if (auth == null) {
                    return ResponseUtil.fail(404, "认证记录不存在");
                }

                studentAuthService.approveAuth(id, admin.getId(), admin.getUsername());

                // 通过认证后奖励50积分
                creditScoreService.updateCreditScore(auth.getUserId(), CreditScoreService.CreditRule.CERTIFICATION_PASS);
            } else {
                // 拒绝审核
                studentAuthService.rejectAuth(id, admin.getId(), admin.getUsername(), reason);
            }

            return ResponseUtil.ok("审核成功");
        } catch (Exception e) {
            logger.error("审核学号认证失败", e);
            return ResponseUtil.fail(500, "审核失败");
        }
    }
}
