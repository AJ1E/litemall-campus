package org.linlinjava.litemall.db.service;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.SicauCommentMapper;
import org.linlinjava.litemall.db.dao.SicauCommentTagMapper;
import org.linlinjava.litemall.db.domain.SicauComment;
import org.linlinjava.litemall.db.domain.SicauCommentExample;
import org.linlinjava.litemall.db.domain.SicauCommentTag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 互评系统服务
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@Service
public class SicauCommentService {
    
    @Resource
    private SicauCommentMapper commentMapper;
    
    @Resource
    private SicauCommentTagMapper commentTagMapper;
    
    /**
     * 根据ID查询评价
     */
    public SicauComment findById(Integer id) {
        return commentMapper.selectByPrimaryKey(id);
    }
    
    /**
     * 根据订单ID查询评价列表
     */
    public List<SicauComment> findByOrderId(Integer orderId) {
        SicauCommentExample example = new SicauCommentExample();
        example.or().andOrderIdEqualTo(orderId).andDeletedEqualTo(false);
        return commentMapper.selectByExample(example);
    }
    
    /**
     * 判断订单是否已评价（根据订单ID和角色）
     */
    public boolean isCommented(Integer orderId, Byte role) {
        SicauComment comment = findByOrderIdAndRole(orderId, role);
        return comment != null;
    }
    
    /**
     * 根据订单ID和角色查询评价
     */
    public SicauComment findByOrderIdAndRole(Integer orderId, Byte role) {
        SicauCommentExample example = new SicauCommentExample();
        example.or().andOrderIdEqualTo(orderId).andRoleEqualTo(role).andDeletedEqualTo(false);
        List<SicauComment> list = commentMapper.selectByExample(example);
        return list.isEmpty() ? null : list.get(0);
    }
    
    /**
     * 查询用户收到的评价列表（分页）
     */
    public List<SicauComment> queryReceivedComments(Integer toUserId, Boolean isAnonymous, 
                                                     Integer page, Integer limit) {
        SicauCommentExample example = new SicauCommentExample();
        SicauCommentExample.Criteria criteria = example.or().andToUserIdEqualTo(toUserId).andDeletedEqualTo(false);
        if (isAnonymous != null) {
            criteria.andIsAnonymousEqualTo(isAnonymous);
        }
        example.setOrderByClause("add_time DESC");
        PageHelper.startPage(page, limit);
        return commentMapper.selectByExample(example);
    }
    
    /**
     * 查询用户发出的评价列表（分页）
     */
    public List<SicauComment> querySentComments(Integer fromUserId, Integer page, Integer limit) {
        SicauCommentExample example = new SicauCommentExample();
        example.or().andFromUserIdEqualTo(fromUserId).andDeletedEqualTo(false);
        example.setOrderByClause("add_time DESC");
        PageHelper.startPage(page, limit);
        return commentMapper.selectByExample(example);
    }
    
    /**
     * 统计用户收到的评价数量
     */
    public long countReceivedComments(Integer toUserId, Boolean isAnonymous) {
        SicauCommentExample example = new SicauCommentExample();
        SicauCommentExample.Criteria criteria = example.or().andToUserIdEqualTo(toUserId).andDeletedEqualTo(false);
        if (isAnonymous != null) {
            criteria.andIsAnonymousEqualTo(isAnonymous);
        }
        return commentMapper.countByExample(example);
    }
    
    /**
     * 统计用户发出的评价数量
     */
    public long countSentComments(Integer fromUserId) {
        SicauCommentExample example = new SicauCommentExample();
        example.or().andFromUserIdEqualTo(fromUserId).andDeletedEqualTo(false);
        return commentMapper.countByExample(example);
    }
    
    /**
     * 计算用户平均评分
     */
    public Double calculateAverageRating(Integer toUserId) {
        SicauCommentExample example = new SicauCommentExample();
        example.or().andToUserIdEqualTo(toUserId).andDeletedEqualTo(false);
        List<SicauComment> comments = commentMapper.selectByExample(example);
        
        if (comments.isEmpty()) {
            return 0.0;
        }
        
        double sum = 0.0;
        for (SicauComment comment : comments) {
            if (comment.getRating() != null) {
                sum += comment.getRating().doubleValue();
            }
        }
        return sum / comments.size();
    }
    
    /**
     * 发布评价
     */
    @Transactional
    public int addComment(SicauComment comment) {
        comment.setAddTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        comment.setDeleted(false);
        return commentMapper.insertSelective(comment);
    }
    
    /**
     * 回复评价
     */
    @Transactional
    public int replyComment(Integer commentId, String reply) {
        SicauComment comment = new SicauComment();
        comment.setId(commentId);
        comment.setReply(reply);
        comment.setUpdateTime(LocalDateTime.now());
        return commentMapper.updateByPrimaryKeySelective(comment);
    }
    
    /**
     * 删除评价（逻辑删除）
     */
    @Transactional
    public int deleteById(Integer id) {
        SicauComment comment = new SicauComment();
        comment.setId(id);
        comment.setDeleted(true);
        comment.setUpdateTime(LocalDateTime.now());
        return commentMapper.updateByPrimaryKeySelective(comment);
    }
    
    /**
     * 根据角色查询评价标签列表
     */
    public List<SicauCommentTag> getTagsByRole(Byte role) {
        return commentTagMapper.selectByRole(role);
    }
    
    /**
     * 查询所有评价标签
     */
    public List<SicauCommentTag> getAllTags() {
        return commentTagMapper.selectAll();
    }
    
    /**
     * 添加评价标签
     */
    @Transactional
    public int addTag(SicauCommentTag tag) {
        tag.setAddTime(LocalDateTime.now());
        tag.setUpdateTime(LocalDateTime.now());
        tag.setDeleted(false);
        return commentTagMapper.insertSelective(tag);
    }
    
    /**
     * 更新评价标签
     */
    @Transactional
    public int updateTag(SicauCommentTag tag) {
        tag.setUpdateTime(LocalDateTime.now());
        return commentTagMapper.updateByPrimaryKeySelective(tag);
    }
    
    /**
     * 删除评价标签（逻辑删除）
     */
    @Transactional
    public int deleteTag(Integer id) {
        SicauCommentTag tag = new SicauCommentTag();
        tag.setId(id);
        tag.setDeleted(true);
        tag.setUpdateTime(LocalDateTime.now());
        return commentTagMapper.updateByPrimaryKeySelective(tag);
    }
}
