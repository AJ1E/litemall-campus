package org.linlinjava.litemall.admin.web;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.SicauCommentTag;
import org.linlinjava.litemall.db.service.SicauCommentService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/sicau/comment")
public class AdminSicauCommentController {

    @Resource
    private SicauCommentService commentService;

    @GetMapping("tags")
    public Object listTags() {
        List<SicauCommentTag> list = commentService.getAllTags();
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        return ResponseUtil.ok(data);
    }

    @PostMapping("tag")
    public Object addTag(@RequestBody SicauCommentTag tag) {
        if (tag == null || tag.getTagName() == null) return ResponseUtil.badArgument();
        int rows = commentService.addTag(tag);
        if (rows <= 0) return ResponseUtil.fail();
        return ResponseUtil.ok(tag);
    }

    @PutMapping("tag")
    public Object updateTag(@RequestBody SicauCommentTag tag) {
        if (tag == null || tag.getId() == null) return ResponseUtil.badArgument();
        int rows = commentService.updateTag(tag);
        if (rows <= 0) return ResponseUtil.fail();
        return ResponseUtil.ok(tag);
    }

    @DeleteMapping("tag/{id}")
    public Object deleteTag(@PathVariable Integer id) {
        if (id == null) return ResponseUtil.badArgument();
        int rows = commentService.deleteTag(id);
        if (rows <= 0) return ResponseUtil.fail();
        return ResponseUtil.ok();
    }
}
