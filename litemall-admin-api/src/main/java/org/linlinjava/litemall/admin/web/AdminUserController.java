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

    @Autowired
    private org.linlinjava.litemall.admin.service.AdminLogService adminLogService;

    @Autowired
    private LitemallAdminService adminService;

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
     * 查询学号认证列表（支持状态筛选）
     * 
     * @param status 认证状态（可选：1-待审核, 2-已通过, 3-已拒绝）
     * @param page 页码
     * @param limit 每页数量
     * @return 认证列表
     */
    @RequiresPermissions("admin:user:list")
    @RequiresPermissionsDesc(menu = {"用户管理", "学号认证审核"}, button = "查询")
    @GetMapping("/authList")
    public Object getAuthList(@RequestParam(required = false) Byte status,
                              @RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "10") Integer limit) {
        List<SicauStudentAuth> authList = studentAuthService.queryByStatus(status, page, limit);
        int total = studentAuthService.countByStatus(status);
        
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
                item.put("status", auth.getStatus());
                item.put("submitTime", auth.getSubmitTime());
                item.put("auditTime", auth.getAuditTime());
                item.put("failReason", auth.getFailReason());
                
                // 获取用户信息
                LitemallUser user = userService.findById(auth.getUserId());
                if (user != null) {
                    item.put("nickname", user.getNickname());
                    item.put("avatar", user.getAvatar());
                }
                
                resultList.add(item);
            } catch (Exception e) {
                logger.error("解密学号认证信息失败: " + e.getMessage());
            }
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("items", resultList);
        data.put("page", page);
        data.put("limit", limit);
        
        return ResponseUtil.ok(data);
    }

    /**
     * 查询待审核的学号认证列表（保留向后兼容）
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
        // 直接调用新接口，status=1表示待审核
        return getAuthList((byte) 1, page, limit);
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

    /**
     * 批量审核学号认证
     * 
     * @param body 包含 ids (数组), status (2-通过, 3-拒绝), reason (拒绝原因)
     * @return 审核结果
     */
    @RequiresPermissions("admin:user:list")
    @RequiresPermissionsDesc(menu = {"用户管理", "学号认证审核"}, button = "批量审核")
    @PostMapping("/batchAuditAuth")
    public Object batchAuditAuth(@RequestBody String body) {
        List<Integer> ids = JacksonUtil.parseIntegerList(body, "ids");
        Integer status = JacksonUtil.parseInteger(body, "status");
        String reason = JacksonUtil.parseString(body, "reason");

        if (ids == null || ids.isEmpty() || status == null) {
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

        int successCount = 0;
        int failCount = 0;
        List<String> failReasons = new ArrayList<>();

        try {
            for (Integer id : ids) {
                try {
                    if (status == 2) {
                        // 通过审核
                        SicauStudentAuth auth = studentAuthService.findById(id);
                        if (auth == null) {
                            failCount++;
                            failReasons.add("ID " + id + ": 认证记录不存在");
                            continue;
                        }

                        studentAuthService.approveAuth(id, admin.getId(), admin.getUsername());

                        // 通过认证后奖励50积分
                        creditScoreService.updateCreditScore(auth.getUserId(), CreditScoreService.CreditRule.CERTIFICATION_PASS);
                        successCount++;
                    } else {
                        // 拒绝审核
                        studentAuthService.rejectAuth(id, admin.getId(), admin.getUsername(), reason);
                        successCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    failReasons.add("ID " + id + ": " + e.getMessage());
                    logger.error("批量审核学号认证失败，ID: " + id, e);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failReasons", failReasons);

            return ResponseUtil.ok(result);
        } catch (Exception e) {
            logger.error("批量审核学号认证失败", e);
            return ResponseUtil.fail(500, "批量审核失败");
        }
    }

    /**
     * 封禁用户
     */
    @RequiresPermissions("admin:user:ban")
    @RequiresPermissionsDesc(menu = {"用户管理", "会员管理"}, button = "封禁")
    @PostMapping("/ban")
    public Object banUser(@RequestBody String body) {
        try {
            Integer userId = JacksonUtil.parseInteger(body, "userId");
            Integer banType = JacksonUtil.parseInteger(body, "banType"); // 1-24h冻结, 2-永久封禁
            String reason = JacksonUtil.parseString(body, "reason");

            if (userId == null || banType == null) {
                return ResponseUtil.badArgument();
            }

            if (StringUtils.isEmpty(reason)) {
                return ResponseUtil.fail(401, "封禁原因不能为空");
            }

            // 获取当前管理员信息
            Subject currentUser = SecurityUtils.getSubject();
            LitemallAdmin admin = (LitemallAdmin) currentUser.getPrincipal();

            // 1. 权限验证：永久封禁需要高级管理员
            if (banType == 2) {
                // 检查是否有高级管理员权限（roleIds包含2或更高级别）
                Integer[] roleIds = admin.getRoleIds();
                boolean isHighLevel = false;
                if (roleIds != null) {
                    for (Integer roleId : roleIds) {
                        // roleId >= 2 表示高级管理员或超级管理员
                        if (roleId >= 2) {
                            isHighLevel = true;
                            break;
                        }
                    }
                }
                
                if (!isHighLevel) {
                    return ResponseUtil.fail(403, "永久封禁需要高级管理员权限");
                }
            }

            // 2. 更新用户封禁状态
            LitemallUser user = userService.findById(userId);
            if (user == null) {
                return ResponseUtil.fail(404, "用户不存在");
            }

            user.setBanStatus(banType.byteValue());
            user.setBanReason(reason);
            user.setBanTime(java.time.LocalDateTime.now());

            if (banType == 1) {
                // 24h 冻结
                user.setBanExpireTime(java.time.LocalDateTime.now().plusHours(24));
            } else {
                // 永久封禁，不设置解封时间
                user.setBanExpireTime(null);
            }

            userService.updateById(user);

            // 3. 记录操作日志
            Map<String, Object> detail = new HashMap<>();
            detail.put("userId", userId);
            detail.put("username", user.getUsername());
            detail.put("banType", banType == 1 ? "24h冻结" : "永久封禁");
            detail.put("reason", reason);
            adminLogService.log(admin.getId(), admin.getUsername(), "ban_user", "user", userId, detail);

            logger.info("管理员 " + admin.getUsername() + " 封禁用户 " + userId + "，类型：" + banType + "，原因：" + reason);

            return ResponseUtil.ok();
        } catch (Exception e) {
            logger.error("封禁用户失败", e);
            return ResponseUtil.fail(500, "封禁用户失败");
        }
    }

    /**
     * 解封用户
     */
    @RequiresPermissions("admin:user:ban")
    @RequiresPermissionsDesc(menu = {"用户管理", "会员管理"}, button = "解封")
    @PostMapping("/unban")
    public Object unbanUser(@RequestBody String body) {
        try {
            Integer userId = JacksonUtil.parseInteger(body, "userId");

            if (userId == null) {
                return ResponseUtil.badArgument();
            }

            // 获取当前管理员信息
            Subject currentUser = SecurityUtils.getSubject();
            LitemallAdmin admin = (LitemallAdmin) currentUser.getPrincipal();

            // 更新用户状态
            LitemallUser user = userService.findById(userId);
            if (user == null) {
                return ResponseUtil.fail(404, "用户不存在");
            }

            user.setBanStatus((byte) 0);
            user.setBanReason(null);
            user.setBanTime(null);
            user.setBanExpireTime(null);

            userService.updateById(user);

            // 记录操作日志
            Map<String, Object> detail = new HashMap<>();
            detail.put("userId", userId);
            detail.put("username", user.getUsername());
            adminLogService.log(admin.getId(), admin.getUsername(), "unban_user", "user", userId, detail);

            logger.info("管理员 " + admin.getUsername() + " 解封用户 " + userId);

            return ResponseUtil.ok();
        } catch (Exception e) {
            logger.error("解封用户失败", e);
            return ResponseUtil.fail(500, "解封用户失败");
        }
    }
}
