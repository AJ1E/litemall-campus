package org.linlinjava.litemall.wx.web;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.SicauComment;
import org.linlinjava.litemall.db.domain.SicauCommentTag;
import org.linlinjava.litemall.db.service.SicauCommentService;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wx/sicau/comment")
public class WxSicauCommentController {

    @Resource
    private SicauCommentService commentService;

    /**
     * 发布互评（买家或卖家基于订单互评）
     */
    @PostMapping("post")
    public Object post(@LoginUser Integer userId, @RequestBody SicauComment comment) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (comment == null || comment.getOrderId() == null || comment.getRole() == null) {
            return ResponseUtil.badArgument();
        }

        // from user must match login user
        comment.setFromUserId(userId);

        int rows = commentService.addComment(comment);
        if (rows <= 0) {
            return ResponseUtil.fail();
        }
        return ResponseUtil.ok(comment);
    }

    /**
     * 根据订单查询互评
     */
    @GetMapping("order/{orderId}")
    public Object byOrder(@PathVariable Integer orderId) {
        if (orderId == null) return ResponseUtil.badArgument();
        List<SicauComment> list = commentService.findByOrderId(orderId);
        return ResponseUtil.okList(list);
    }

    /**
     * 我收到的评价（作为被评价方）
     */
    @GetMapping("received")
    public Object received(@LoginUser Integer userId,
                           @RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "10") Integer limit,
                           @RequestParam(required = false) Boolean isAnonymous) {
        if (userId == null) return ResponseUtil.unlogin();
        List<SicauComment> list = commentService.queryReceivedComments(userId, isAnonymous, page, limit);
        return ResponseUtil.okList(list);
    }

    /**
     * 我发出的评价（作为评价方）
     */
    @GetMapping("sent")
    public Object sent(@LoginUser Integer userId,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        if (userId == null) return ResponseUtil.unlogin();
        List<SicauComment> list = commentService.querySentComments(userId, page, limit);
        return ResponseUtil.okList(list);
    }

    /**
     * 评价标签列表（可按角色过滤）
     */
    @GetMapping("tags")
    public Object tags(@RequestParam(required = false) Byte role) {
        List<SicauCommentTag> list;
        if (role == null) {
            list = commentService.getAllTags();
        } else {
            list = commentService.getTagsByRole(role);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        return ResponseUtil.ok(data);
    }
}
