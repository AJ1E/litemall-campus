package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.SicauAuctionMapper;
import org.linlinjava.litemall.db.dao.SicauAuctionBidMapper;
import org.linlinjava.litemall.db.domain.SicauAuction;
import org.linlinjava.litemall.db.domain.SicauAuctionExample;
import org.linlinjava.litemall.db.domain.SicauAuctionBid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 拍卖服务类
 * 
 * Story 5.1: 发起拍卖
 * - 创建拍卖记录
 * - 计算保证金（10% 起拍价）
 * - 设置拍卖时长（12/24/48 小时）
 */
@Service
public class SicauAuctionService {
    
    @Resource
    private SicauAuctionMapper auctionMapper;
    
    @Resource
    private SicauAuctionBidMapper bidMapper;
    
    /**
     * 根据ID查询拍卖
     */
    public SicauAuction findById(Integer id) {
        return auctionMapper.selectByPrimaryKey(id);
    }
    
    /**
     * 根据商品ID查询拍卖
     */
    public SicauAuction findByGoodsId(Integer goodsId) {
        SicauAuctionExample example = new SicauAuctionExample();
        example.or().andGoodsIdEqualTo(goodsId).andDeletedEqualTo(false);
        List<SicauAuction> list = auctionMapper.selectByExample(example);
        return list.isEmpty() ? null : list.get(0);
    }
    
    /**
     * 创建拍卖（Story 5.1）
     * 
     * @param sellerId 卖家用户ID
     * @param goodsId 商品ID
     * @param startPrice 起拍价
     * @param increment 加价幅度
     * @param durationHours 拍卖时长（小时）
     * @return 拍卖ID
     */
    @Transactional
    public Integer createAuction(Integer sellerId, Integer goodsId, 
                                  BigDecimal startPrice, BigDecimal increment, 
                                  Integer durationHours) {
        // 1. 验证拍卖时长（只允许 12/24/48 小时）
        if (durationHours != 12 && durationHours != 24 && durationHours != 48) {
            throw new RuntimeException("拍卖时长只能是 12、24 或 48 小时");
        }
        
        // 2. 验证起拍价和加价幅度
        if (startPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("起拍价必须大于 0");
        }
        
        if (increment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("加价幅度必须大于 0");
        }
        
        // 3. 计算保证金（10% 起拍价）
        BigDecimal deposit = startPrice.multiply(new BigDecimal("0.1"))
                                      .setScale(2, BigDecimal.ROUND_HALF_UP);
        
        // 4. 创建拍卖记录
        SicauAuction auction = new SicauAuction();
        auction.setGoodsId(goodsId);
        auction.setSellerId(sellerId);
        auction.setStartPrice(startPrice);
        auction.setCurrentPrice(startPrice); // 初始最高价=起拍价
        auction.setIncrement(increment);
        auction.setDeposit(deposit);
        auction.setDepositStatus((byte) 0); // 待支付保证金
        auction.setStatus((byte) 0); // 待支付保证金
        auction.setDurationHours(durationHours);
        auction.setExtendCount(0);
        auction.setTotalBids(0);
        auction.setDeleted(false);
        auction.setAddTime(LocalDateTime.now());
        auction.setUpdateTime(LocalDateTime.now());
        
        auctionMapper.insertSelective(auction);
        
        return auction.getId();
    }
    
    /**
     * 支付保证金后启动拍卖（回调处理）
     * 
     * @param auctionId 拍卖ID
     */
    @Transactional
    public void startAuctionAfterDepositPaid(Integer auctionId) {
        SicauAuction auction = auctionMapper.selectByPrimaryKey(auctionId);
        
        if (auction == null) {
            throw new RuntimeException("拍卖不存在");
        }
        
        if (auction.getDepositStatus() != 0) {
            throw new RuntimeException("保证金状态异常");
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = now.plusHours(auction.getDurationHours());
        
        // 更新拍卖状态
        auction.setDepositStatus((byte) 1); // 已支付保证金
        auction.setStatus((byte) 1); // 拍卖进行中
        auction.setStartTime(now);
        auction.setEndTime(endTime);
        auction.setUpdateTime(now);
        
        auctionMapper.updateByPrimaryKeySelective(auction);
    }
    
    /**
     * 查询卖家的拍卖列表
     */
    public List<SicauAuction> queryBySellerId(Integer sellerId) {
        SicauAuctionExample example = new SicauAuctionExample();
        example.or().andSellerIdEqualTo(sellerId).andDeletedEqualTo(false);
        example.setOrderByClause("add_time DESC");
        return auctionMapper.selectByExample(example);
    }
    
    /**
     * 查询进行中的拍卖列表
     */
    public List<SicauAuction> queryOngoingAuctions() {
        SicauAuctionExample example = new SicauAuctionExample();
        example.or().andStatusEqualTo((byte) 1).andDeletedEqualTo(false);
        example.setOrderByClause("end_time ASC");
        return auctionMapper.selectByExample(example);
    }
    
    /**
     * 查询已到期但未结算的拍卖（Story 5.3）
     */
    public List<SicauAuction> queryExpiredAuctions() {
        LocalDateTime now = LocalDateTime.now();
        SicauAuctionExample example = new SicauAuctionExample();
        example.or()
            .andStatusEqualTo((byte) 1) // 状态=进行中
            .andDeletedEqualTo(false);
        
        List<SicauAuction> ongoingAuctions = auctionMapper.selectByExample(example);
        
        // 过滤出已过期的拍卖
        List<SicauAuction> expiredAuctions = new java.util.ArrayList<>();
        for (SicauAuction auction : ongoingAuctions) {
            if (auction.getEndTime() != null && now.isAfter(auction.getEndTime())) {
                expiredAuctions.add(auction);
            }
        }
        
        return expiredAuctions;
    }
    
    /**
     * 更新拍卖
     */
    public int updateById(SicauAuction auction) {
        auction.setUpdateTime(LocalDateTime.now());
        return auctionMapper.updateByPrimaryKeySelective(auction);
    }
    
    /**
     * 删除拍卖（逻辑删除）
     */
    public void deleteById(Integer id) {
        SicauAuction auction = new SicauAuction();
        auction.setId(id);
        auction.setDeleted(true);
        auction.setUpdateTime(LocalDateTime.now());
        auctionMapper.updateByPrimaryKeySelective(auction);
    }
    
    /**
     * 出价（Story 5.2）
     * 
     * 业务规则：
     * 1. 出价必须 >= 当前价 + 加价幅度
     * 2. 最后 5 分钟内出价，延长 5 分钟（最多延长 3 次）
     * 3. 记录出价历史
     * 4. 更新当前最高价和最高出价者
     * 
     * 注意：此方法需要配合分布式锁使用，由 Controller 层调用时加锁
     * 
     * @param auctionId 拍卖ID
     * @param bidderId 出价者ID
     * @param bidPrice 出价金额
     * @return 是否出价成功
     */
    @Transactional
    public boolean placeBid(Integer auctionId, Integer bidderId, BigDecimal bidPrice) {
        LocalDateTime now = LocalDateTime.now();
        
        // 1. 查询拍卖
        SicauAuction auction = auctionMapper.selectByPrimaryKey(auctionId);
        if (auction == null) {
            throw new RuntimeException("拍卖不存在");
        }
        
        // 2. 检查拍卖状态（必须是进行中）
        if (auction.getStatus() != 1) {
            throw new RuntimeException("拍卖未开始或已结束");
        }
        
        // 3. 检查是否已过期
        if (now.isAfter(auction.getEndTime())) {
            throw new RuntimeException("拍卖已结束");
        }
        
        // 4. 检查出价是否有效（必须 >= 当前价 + 加价幅度）
        BigDecimal minBidPrice = auction.getCurrentPrice().add(auction.getIncrement());
        if (bidPrice.compareTo(minBidPrice) < 0) {
            throw new RuntimeException(
                String.format("出价必须不低于 %.2f 元（当前价 %.2f + 加价幅度 %.2f）", 
                    minBidPrice, auction.getCurrentPrice(), auction.getIncrement())
            );
        }
        
        // 5. 检查是否是卖家自己出价
        if (bidderId.equals(auction.getSellerId())) {
            throw new RuntimeException("卖家不能对自己的拍卖出价");
        }
        
        // 6. 记录出价
        SicauAuctionBid bid = new SicauAuctionBid();
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setBidPrice(bidPrice);
        bid.setIsAutoBid(false);
        bid.setBidTime(now);
        bidMapper.insertSelective(bid);
        
        // 7. 更新拍卖信息
        auction.setCurrentPrice(bidPrice);
        auction.setHighestBidderId(bidderId);
        auction.setTotalBids(auction.getTotalBids() + 1);
        
        // 8. 检查是否需要延时（最后 5 分钟内出价）
        long minutesToEnd = ChronoUnit.MINUTES.between(now, auction.getEndTime());
        if (minutesToEnd >= 0 && minutesToEnd < 5 && auction.getExtendCount() < 3) {
            // 延长 5 分钟
            LocalDateTime newEndTime = auction.getEndTime().plusMinutes(5);
            auction.setEndTime(newEndTime);
            auction.setExtendCount(auction.getExtendCount() + 1);
        }
        
        auction.setUpdateTime(now);
        auctionMapper.updateByPrimaryKeySelective(auction);
        
        return true;
    }
    
    /**
     * 计算最低出价金额
     */
    public BigDecimal getMinBidPrice(Integer auctionId) {
        SicauAuction auction = auctionMapper.selectByPrimaryKey(auctionId);
        if (auction == null) {
            return BigDecimal.ZERO;
        }
        return auction.getCurrentPrice().add(auction.getIncrement());
    }
    
    /**
     * 根据状态查询拍卖列表（Story 5.3）
     */
    public List<SicauAuction> queryByStatus(Byte status) {
        SicauAuctionExample example = new SicauAuctionExample();
        example.or().andStatusEqualTo(status).andDeletedEqualTo(false);
        example.setOrderByClause("update_time DESC");
        return auctionMapper.selectByExample(example);
    }
    
    /**
     * 查询已结束的拍卖（成交+流拍）（Story 5.3）
     */
    public List<SicauAuction> queryFinishedAuctions() {
        SicauAuctionExample example = new SicauAuctionExample();
        example.or().andStatusEqualTo((byte) 3).andDeletedEqualTo(false);
        example.or().andStatusEqualTo((byte) 4).andDeletedEqualTo(false);
        example.setOrderByClause("update_time DESC");
        return auctionMapper.selectByExample(example);
    }
    
    /**
     * 统计拍卖总数（Story 5.4）
     */
    public int countTotal() {
        SicauAuctionExample example = new SicauAuctionExample();
        example.or().andDeletedEqualTo(false);
        return (int) auctionMapper.countByExample(example);
    }
    
    /**
     * 统计成交拍卖数（Story 5.4）
     */
    public int countSold() {
        SicauAuctionExample example = new SicauAuctionExample();
        example.or().andStatusEqualTo((byte) 3).andDeletedEqualTo(false);
        return (int) auctionMapper.countByExample(example);
    }
    
    /**
     * 统计流拍拍卖数（Story 5.4）
     */
    public int countUnsold() {
        SicauAuctionExample example = new SicauAuctionExample();
        example.or().andStatusEqualTo((byte) 4).andDeletedEqualTo(false);
        return (int) auctionMapper.countByExample(example);
    }
    
    /**
     * 查询所有已成交的拍卖（用于计算溢价率）（Story 5.4）
     */
    public List<SicauAuction> queryAllSoldAuctions() {
        SicauAuctionExample example = new SicauAuctionExample();
        example.or().andStatusEqualTo((byte) 3).andDeletedEqualTo(false);
        return auctionMapper.selectByExample(example);
    }
}
