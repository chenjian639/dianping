package com.dianping.service;

import com.dianping.common.PageResult;
import com.dianping.dto.CreateReviewDTO;
import com.dianping.entity.Order;
import com.dianping.entity.Review;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.OrderMapper;
import com.dianping.mapper.ReviewMapper;
import com.dianping.vo.ReviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private OrderMapper orderMapper;

    public Long createReview(
            Long userId,
            CreateReviewDTO dto) {

        // 1. 必须是当前用户自己的、已经完成的订单
        Order order =
                orderMapper.selectCompletedOrderByUser(
                        dto.getOrderId(),
                        userId
                );

        if (order == null) {
            throw new BusinessException(
                    "订单不存在、无权限或尚未完成"
            );
        }

        // 2. 一个订单不能重复评价
        int count =
                reviewMapper.countByOrderId(
                        dto.getOrderId()
                );

        if (count > 0) {
            throw new BusinessException("该订单已经评价");
        }

        // 3. 创建评价
        Review review = new Review();

        review.setUserId(userId);

        // restaurantId 从订单获取，而不是相信前端
        review.setRestaurantId(order.getRestaurantId());

        review.setOrderId(order.getId());
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());

        // 4. 写数据库
        reviewMapper.insert(review);

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
}