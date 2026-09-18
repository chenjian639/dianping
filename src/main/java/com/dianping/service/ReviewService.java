package com.dianping.service;

import com.dianping.common.PageResult;
import com.dianping.dto.CreateReviewDTO;
import com.dianping.entity.Order;
import com.dianping.entity.Review;
import com.dianping.enums.OrderStatus;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.OrderMapper;
import com.dianping.mapper.RestaurantMapper;
import com.dianping.mapper.ReviewMapper;
import com.dianping.vo.MyReviewVO;
import com.dianping.vo.RestaurantReviewSummaryVO;
import com.dianping.vo.ReviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewService {

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RestaurantMapper restaurantMapper;

    @Autowired
    private RestaurantCacheService restaurantCacheService;

    @Transactional
    public Long createReview(
            Long userId,
            CreateReviewDTO dto) {

        // 1. 必须是当前用户自己的、已经完成的订单
        Order order =
                orderMapper.selectCompletedOrderByUser(
                        dto.getOrderId(),
                        userId,
                        OrderStatus.COMPLETED.getCode()
                );

        if (order == null) {
            throw new BusinessException(
                    "订单不存在、无权限或尚未完成"
            );
        }

        // 2. 创建评价
        //    唯一性由 review.uk_order_id 保证，并发下重复评价会抛
        //    DuplicateKeyException，这里转成业务异常，避免冒泡成 500
        Review review = new Review();

        review.setUserId(userId);

        // restaurantId 从订单获取，而不是相信前端
        review.setRestaurantId(order.getRestaurantId());

        review.setOrderId(order.getId());
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());

        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该订单已经评价");
        }

        // 3. 同步维护餐厅评分统计
        int rows =
                restaurantMapper.addReviewStatistics(
                        order.getRestaurantId(),
                        dto.getRating()
                );

        if (rows == 0) {
            throw new BusinessException(
                    "更新餐厅评分统计失败"
            );
        }
        // 4. 删除餐厅详情缓存（走熔断保护，Redis 故障时降级跳过）
        String key = "restaurant:detail:" + order.getRestaurantId();

        try {
            restaurantCacheService.delete(key);
        } catch (Exception e) {
            // 缓存删除失败不影响评价结果
        }
        return review.getId();
    }

    public PageResult<ReviewVO> getRestaurantReviews(
            Long restaurantId,
            Integer page,
            Integer pageSize) {

        if (page < 1) {
            throw new BusinessException("page不能小于1");
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException("pageSize必须在1到100之间");
        }

        int offset = (page - 1) * pageSize;

        List<ReviewVO> records =
                reviewMapper.selectByRestaurantIdPage(
                        restaurantId,
                        offset,
                        pageSize
                );

        Long total =
                reviewMapper.countByRestaurantId(
                        restaurantId
                );

        PageResult<ReviewVO> result = new PageResult<>();
        result.setTotal(total);
        result.setRecords(records);

        return result;
    }

    public RestaurantReviewSummaryVO getRestaurantReviewSummary(
            Long restaurantId) {

        return reviewMapper.getRestaurantReviewSummary(restaurantId);
    }

    public PageResult<MyReviewVO> getMyReviews(
            Long userId,
            Integer page,
            Integer pageSize) {

        if (page < 1) {
            throw new BusinessException("page不能小于1");
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "pageSize必须在1到100之间"
            );
        }

        int offset = (page - 1) * pageSize;

        List<MyReviewVO> records =
                reviewMapper.selectMyReviewsPage(
                        userId,
                        offset,
                        pageSize
                );

        Long total =
                reviewMapper.countMyReviews(userId);

        PageResult<MyReviewVO> result =
                new PageResult<>();

        result.setTotal(total);
        result.setRecords(records);

        return result;
    }
}
