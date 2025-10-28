package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.SicauAuctionBidMapper;
import org.linlinjava.litemall.db.domain.SicauAuctionBid;
import org.linlinjava.litemall.db.domain.SicauAuctionBidExample;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 拍卖出价服务类
 * 
 * Story 5.2: 参与竞拍
 * - 记录出价历史
 * - 查询出价记录
 */
@Service
public class SicauAuctionBidService {
    
    @Resource
    private SicauAuctionBidMapper bidMapper;
    
    /**
     * 添加出价记录
     */
    public void add(SicauAuctionBid bid) {
        bid.setBidTime(LocalDateTime.now());
        bidMapper.insertSelective(bid);
    }
    
    /**
     * 查询拍卖的出价记录（按时间倒序）
     * 
     * @param auctionId 拍卖ID
     * @param limit 限制条数
     */
    public List<SicauAuctionBid> queryByAuctionId(Integer auctionId, Integer limit) {
        SicauAuctionBidExample example = new SicauAuctionBidExample();
        example.or().andAuctionIdEqualTo(auctionId);
        example.setOrderByClause("bid_time DESC");
        
        if (limit != null && limit > 0) {
            // MyBatis Generator 不直接支持 LIMIT，需要使用分页
            // 这里简化处理，返回所有记录后在业务层截取
        }
        
        List<SicauAuctionBid> bids = bidMapper.selectByExample(example);
        
        // 如果指定了 limit，则只返回前 N 条
        if (limit != null && limit > 0 && bids.size() > limit) {
            return bids.subList(0, limit);
        }
        
        return bids;
    }
    
    /**
     * 查询用户的出价记录
     */
    public List<SicauAuctionBid> queryByBidderId(Integer bidderId) {
        SicauAuctionBidExample example = new SicauAuctionBidExample();
        example.or().andBidderIdEqualTo(bidderId);
        example.setOrderByClause("bid_time DESC");
        return bidMapper.selectByExample(example);
    }
    
    /**
     * 查询拍卖的出价总次数
     */
    public int countByAuctionId(Integer auctionId) {
        SicauAuctionBidExample example = new SicauAuctionBidExample();
        example.or().andAuctionIdEqualTo(auctionId);
        return (int) bidMapper.countByExample(example);
    }
}
