package org.linlinjava.litemall.wx.web;

import com.github.pagehelper.PageInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.util.SensitiveWordFilter;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.db.service.*;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 商品服务
 */
@RestController
@RequestMapping("/wx/goods")
@Validated
public class WxGoodsController {
	private final Log logger = LogFactory.getLog(WxGoodsController.class);

	@Autowired
	private LitemallGoodsService goodsService;

	@Autowired
	private LitemallGoodsProductService productService;

	@Autowired
	private LitemallIssueService goodsIssueService;

	@Autowired
	private LitemallGoodsAttributeService goodsAttributeService;

	@Autowired
	private LitemallBrandService brandService;

	@Autowired
	private LitemallCommentService commentService;

	@Autowired
	private LitemallUserService userService;

	@Autowired
	private LitemallCollectService collectService;

	@Autowired
	private LitemallFootprintService footprintService;

	@Autowired
	private LitemallCategoryService categoryService;

	@Autowired
	private LitemallSearchHistoryService searchHistoryService;

	@Autowired
	private LitemallGoodsSpecificationService goodsSpecificationService;

	@Autowired
	private LitemallGrouponRulesService rulesService;

	@Autowired
	private SensitiveWordFilter sensitiveWordFilter;

	private final static ArrayBlockingQueue<Runnable> WORK_QUEUE = new ArrayBlockingQueue<>(9);

	private final static RejectedExecutionHandler HANDLER = new ThreadPoolExecutor.CallerRunsPolicy();

	private static ThreadPoolExecutor executorService = new ThreadPoolExecutor(16, 16, 1000, TimeUnit.MILLISECONDS, WORK_QUEUE, HANDLER);

	/**
	 * 商品详情
	 * <p>
	 * 用户可以不登录。
	 * 如果用户登录，则记录用户足迹以及返回用户收藏信息。
	 *
	 * @param userId 用户ID
	 * @param id     商品ID
	 * @return 商品详情
	 */
	@GetMapping("detail")
	public Object detail(@LoginUser Integer userId, @NotNull Integer id) {
		// 商品信息
		LitemallGoods info = goodsService.findById(id);

		// 商品属性
		Callable<List> goodsAttributeListCallable = () -> goodsAttributeService.queryByGid(id);

		// 商品规格 返回的是定制的GoodsSpecificationVo
		Callable<Object> objectCallable = () -> goodsSpecificationService.getSpecificationVoList(id);

		// 商品规格对应的数量和价格
		Callable<List> productListCallable = () -> productService.queryByGid(id);

		// 商品问题，这里是一些通用问题
		Callable<List> issueCallable = () -> goodsIssueService.querySelective("", 1, 4, "", "");

		// 商品品牌商
		Callable<LitemallBrand> brandCallable = ()->{
			Integer brandId = info.getBrandId();
			LitemallBrand brand;
			if (brandId == 0) {
				brand = new LitemallBrand();
			} else {
				brand = brandService.findById(info.getBrandId());
			}
			return brand;
		};

		// 评论
		Callable<Map> commentsCallable = () -> {
			List<LitemallComment> comments = commentService.queryGoodsByGid(id, 0, 2);
			List<Map<String, Object>> commentsVo = new ArrayList<>(comments.size());
			long commentCount = PageInfo.of(comments).getTotal();
			for (LitemallComment comment : comments) {
				Map<String, Object> c = new HashMap<>();
				c.put("id", comment.getId());
				c.put("addTime", comment.getAddTime());
				c.put("content", comment.getContent());
				c.put("adminContent", comment.getAdminContent());
				LitemallUser user = userService.findById(comment.getUserId());
				c.put("nickname", user == null ? "" : user.getNickname());
				c.put("avatar", user == null ? "" : user.getAvatar());
				c.put("picList", comment.getPicUrls());
				commentsVo.add(c);
			}
			Map<String, Object> commentList = new HashMap<>();
			commentList.put("count", commentCount);
			commentList.put("data", commentsVo);
			return commentList;
		};

		//团购信息
		Callable<List> grouponRulesCallable = () ->rulesService.queryByGoodsId(id);

		// 用户收藏
		int userHasCollect = 0;
		if (userId != null) {
			userHasCollect = collectService.count(userId, (byte)0, id);
		}

		// 记录用户的足迹 异步处理
		if (userId != null) {
			executorService.execute(()->{
				LitemallFootprint footprint = new LitemallFootprint();
				footprint.setUserId(userId);
				footprint.setGoodsId(id);
				footprintService.add(footprint);
			});
		}
		FutureTask<List> goodsAttributeListTask = new FutureTask<>(goodsAttributeListCallable);
		FutureTask<Object> objectCallableTask = new FutureTask<>(objectCallable);
		FutureTask<List> productListCallableTask = new FutureTask<>(productListCallable);
		FutureTask<List> issueCallableTask = new FutureTask<>(issueCallable);
		FutureTask<Map> commentsCallableTsk = new FutureTask<>(commentsCallable);
		FutureTask<LitemallBrand> brandCallableTask = new FutureTask<>(brandCallable);
        FutureTask<List> grouponRulesCallableTask = new FutureTask<>(grouponRulesCallable);

		executorService.submit(goodsAttributeListTask);
		executorService.submit(objectCallableTask);
		executorService.submit(productListCallableTask);
		executorService.submit(issueCallableTask);
		executorService.submit(commentsCallableTsk);
		executorService.submit(brandCallableTask);
		executorService.submit(grouponRulesCallableTask);

		Map<String, Object> data = new HashMap<>();

		try {
			data.put("info", info);
			data.put("userHasCollect", userHasCollect);
			data.put("issue", issueCallableTask.get());
			data.put("comment", commentsCallableTsk.get());
			data.put("specificationList", objectCallableTask.get());
			data.put("productList", productListCallableTask.get());
			data.put("attribute", goodsAttributeListTask.get());
			data.put("brand", brandCallableTask.get());
			data.put("groupon", grouponRulesCallableTask.get());
			//SystemConfig.isAutoCreateShareImage()
			data.put("share", SystemConfig.isAutoCreateShareImage());

		}
		catch (Exception e) {
			e.printStackTrace();
		}

		//商品分享图片地址
		data.put("shareImage", info.getShareUrl());
		return ResponseUtil.ok(data);
	}

	/**
	 * 商品分类类目
	 *
	 * @param id 分类类目ID
	 * @return 商品分类类目
	 */
	@GetMapping("category")
	public Object category(@NotNull Integer id) {
		LitemallCategory cur = categoryService.findById(id);
		LitemallCategory parent = null;
		List<LitemallCategory> children = null;

		if (cur.getPid() == 0) {
			parent = cur;
			children = categoryService.queryByPid(cur.getId());
			cur = children.size() > 0 ? children.get(0) : cur;
		} else {
			parent = categoryService.findById(cur.getPid());
			children = categoryService.queryByPid(cur.getPid());
		}
		Map<String, Object> data = new HashMap<>();
		data.put("currentCategory", cur);
		data.put("parentCategory", parent);
		data.put("brotherCategory", children);
		return ResponseUtil.ok(data);
	}

	/**
	 * 根据条件搜素商品
	 * <p>
	 * 1. 这里的前五个参数都是可选的，甚至都是空
	 * 2. 用户是可选登录，如果登录，则记录用户的搜索关键字
	 *
	 * @param categoryId 分类类目ID，可选
	 * @param brandId    品牌商ID，可选
	 * @param keyword    关键字，可选
	 * @param isNew      是否新品，可选
	 * @param isHot      是否热买，可选
	 * @param userId     用户ID
	 * @param page       分页页数
	 * @param limit       分页大小
	 * @param sort       排序方式，支持"add_time", "retail_price"或"name"
	 * @param order      排序类型，顺序或者降序
	 * @return 根据条件搜素的商品详情
	 */
	@GetMapping("list")
	public Object list(
		Integer categoryId,
		Integer brandId,
		String keyword,
		Boolean isNew,
		Boolean isHot,
		@LoginUser Integer userId,
		@RequestParam(defaultValue = "1") Integer page,
		@RequestParam(defaultValue = "10") Integer limit,
		@Sort(accepts = {"add_time", "retail_price", "name"}) @RequestParam(defaultValue = "add_time") String sort,
		@Order @RequestParam(defaultValue = "desc") String order) {

		//添加到搜索历史
		if (userId != null && !StringUtils.isEmpty(keyword)) {
			LitemallSearchHistory searchHistoryVo = new LitemallSearchHistory();
			searchHistoryVo.setKeyword(keyword);
			searchHistoryVo.setUserId(userId);
			searchHistoryVo.setFrom("wx");
			searchHistoryService.save(searchHistoryVo);
		}

		//查询列表数据
		List<LitemallGoods> goodsList = goodsService.querySelective(categoryId, brandId, keyword, isHot, isNew, page, limit, sort, order);

		// 查询商品所属类目列表。
		List<Integer> goodsCatIds = goodsService.getCatIds(brandId, keyword, isHot, isNew);
		List<LitemallCategory> categoryList = null;
		if (goodsCatIds.size() != 0) {
			categoryList = categoryService.queryL2ByIds(goodsCatIds);
		} else {
			categoryList = new ArrayList<>(0);
		}

		PageInfo<LitemallGoods> pagedList = PageInfo.of(goodsList);

		Map<String, Object> entity = new HashMap<>();
		entity.put("list", goodsList);
		entity.put("total", pagedList.getTotal());
		entity.put("page", pagedList.getPageNum());
		entity.put("limit", pagedList.getPageSize());
		entity.put("pages", pagedList.getPages());
		entity.put("filterCategoryList", categoryList);

		// 因为这里需要返回额外的filterCategoryList参数，因此不能方便使用ResponseUtil.okList
		return ResponseUtil.ok(entity);
	}

	/**
	 * 商品详情页面“大家都在看”推荐商品
	 *
	 * @param id, 商品ID
	 * @return 商品详情页面推荐商品
	 */
	@GetMapping("related")
	public Object related(@NotNull Integer id) {
		LitemallGoods goods = goodsService.findById(id);
		if (goods == null) {
			return ResponseUtil.badArgumentValue();
		}

		// 目前的商品推荐算法仅仅是推荐同类目的其他商品
		int cid = goods.getCategoryId();

		// 查找六个相关商品
		int related = 6;
		List<LitemallGoods> goodsList = goodsService.queryByCategory(cid, 0, related);
		return ResponseUtil.okList(goodsList);
	}

	/**
	 * 在售的商品总数
	 *
	 * @return 在售的商品总数
	 */
	@GetMapping("count")
	public Object count() {
		Integer goodsCount = goodsService.queryOnSale();
		return ResponseUtil.ok(goodsCount);
	}

	/**
	 * 发布二手商品
	 * 
	 * @param userId 用户ID（从登录态获取）
	 * @param goods 商品信息
	 * @return 发布结果
	 */
	/**
	 * 商品发布 (Story 2.1)
	 * 
	 * 验收标准:
	 * - 必填字段: 标题(≤30字)、价格(≥1元)、分类、新旧程度
	 * - 可选字段: 原价、购买时间、详细描述(≤500字)
	 * - 支持上传 1-9 张图片，单张 ≤ 5MB
	 * - 实时检测敏感词
	 * 
	 * @param userId 用户ID
	 * @param goods 商品信息
	 * @return 发布结果
	 */
	@PostMapping("publish")
	public Object publish(@LoginUser Integer userId, @RequestBody LitemallGoods goods) {
		// 1. 校验用户登录
		if (userId == null) {
			return ResponseUtil.unlogin();
		}
		
		// 2. 校验必填字段
		if (StringUtils.isEmpty(goods.getName())) {
			return ResponseUtil.badArgumentValue("商品名称不能为空");
		}
		if (goods.getName().length() > 30) {
			return ResponseUtil.badArgumentValue("商品名称不能超过30个字符");
		}
		if (goods.getCategoryId() == null) {
			return ResponseUtil.badArgumentValue("请选择商品分类");
		}
		if (goods.getRetailPrice() == null) {
			return ResponseUtil.badArgumentValue("请填写出售价格");
		}
		if (goods.getRetailPrice().doubleValue() < 1.0) {
			return ResponseUtil.badArgumentValue("出售价格不能低于1元");
		}
		if (goods.getNewness() == null) {
			return ResponseUtil.badArgumentValue("请选择新旧程度");
		}
		if (goods.getNewness() < 1 || goods.getNewness() > 4) {
			return ResponseUtil.badArgumentValue("新旧程度参数错误(1-4)");
		}
		if (StringUtils.isEmpty(goods.getPicUrl())) {
			return ResponseUtil.badArgumentValue("请上传商品图片");
		}
		
		// 3. 校验可选字段
		if (goods.getDetail() != null && goods.getDetail().length() > 500) {
			return ResponseUtil.badArgumentValue("商品描述不能超过500个字符");
		}
		
		// 4. 校验图片数量(1-9张)
		if (goods.getGallery() != null && goods.getGallery().length > 9) {
			return ResponseUtil.badArgumentValue("商品图片不能超过9张");
		}
		
		// 5. 敏感词检测
		String textToCheck = goods.getName() + " " + 
		                    (goods.getBrief() != null ? goods.getBrief() : "") + " " +
		                    (goods.getDetail() != null ? goods.getDetail() : "");
		
		if (sensitiveWordFilter.containsSensitive(textToCheck)) {
			List<String> sensitiveWords = sensitiveWordFilter.getSensitiveWords(textToCheck);
			logger.warn("用户 " + userId + " 发布商品包含敏感词: " + sensitiveWords);
			
			return ResponseUtil.fail(600, "商品信息包含敏感词: " + String.join(", ", sensitiveWords));
		}
		
		// 6. 设置商品基本信息
		goods.setUserId(userId);
		goods.setStatus((byte) 0); // 0-待审核
		goods.setIsOnSale(false); // 默认不上架，需审核通过
		goods.setAddTime(LocalDateTime.now());
		goods.setUpdateTime(LocalDateTime.now());
		goods.setDeleted(false);
		
		// 7. 生成商品编号
		String goodsSn = generateGoodsSn();
		goods.setGoodsSn(goodsSn);
		
		// 8. 保存商品
		goodsService.add(goods);
		
		// 9. 返回成功结果
		Map<String, Object> data = new HashMap<>();
		data.put("id", goods.getId());
		data.put("goodsSn", goods.getGoodsSn());
		data.put("status", goods.getStatus());
		
		return ResponseUtil.ok(data);
	}
	
	/**
	 * 生成商品编号
	 */
	private String generateGoodsSn() {
		return "SH" + System.currentTimeMillis();
	}
	
	/**
	 * 我的发布
	 * 
	 * @param userId 用户ID
	 * @param page 页码
	 * @param limit 每页数量
	 * @return 我发布的商品列表
	 */
	@GetMapping("myPublish")
	public Object myPublish(@LoginUser Integer userId,
	                       @RequestParam(defaultValue = "1") Integer page,
	                       @RequestParam(defaultValue = "10") Integer limit) {
		if (userId == null) {
			return ResponseUtil.unlogin();
		}
		
		List<LitemallGoods> goodsList = goodsService.queryByUserId(userId, page, limit);
		PageInfo<LitemallGoods> pagedList = PageInfo.of(goodsList);
		
		Map<String, Object> data = new HashMap<>();
		data.put("list", goodsList);
		data.put("total", pagedList.getTotal());
		data.put("page", pagedList.getPageNum());
		data.put("limit", pagedList.getPageSize());
		data.put("pages", pagedList.getPages());
		
		return ResponseUtil.ok(data);
	}

}