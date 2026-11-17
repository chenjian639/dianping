package com.trade.service;

import com.trade.common.PageResult;
import com.trade.dto.CreateReviewDTO;
import com.trade.entity.Order;
import com.trade.entity.Review;
import com.trade.enums.OrderStatus;
import com.trade.exception.BusinessException;
import com.trade.mapper.OrderMapper;
import com.trade.mapper.MerchantMapper;
import com.trade.mapper.ReviewMapper;
import com.trade.vo.MyReviewVO;
import com.trade.vo.MerchantReviewSummaryVO;
import com.trade.vo.ReviewVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@lombok.RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;

    private final OrderMapper orderMapper;

    private final MerchantMapper merchantMapper;

    private final OutboxEventService outboxEventService;

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

        // merchantId 从订单获取，而不是相信前端
        review.setMerchantId(order.getMerchantId());

        review.setOrderId(order.getId());
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());

        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该订单已经评价");
        }

        // 3. 同步维护商家评分统计
        int rows =
                merchantMapper.addReviewStatistics(
                        order.getMerchantId(),
                        dto.getRating()
                );

        if (rows == 0) {
            throw new BusinessException(
                    "更新商家评分统计失败"
            );
        }
        outboxEventService.merchantChanged(order.getMerchantId());
        return review.getId();
    }

    public PageResult<ReviewVO> getMerchantReviews(
            Long merchantId,
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
                reviewMapper.selectByMerchantIdPage(
                        merchantId,
                        offset,
                        pageSize
                );

        Long total =
                reviewMapper.countByMerchantId(
                        merchantId
                );

        PageResult<ReviewVO> result = new PageResult<>();
        result.setTotal(total);
        result.setRecords(records);

        return result;
    }

    public MerchantReviewSummaryVO getMerchantReviewSummary(
            Long merchantId) {

        MerchantReviewSummaryVO summary = merchantMapper.selectReviewSummary(merchantId);
        if (summary == null) throw new BusinessException(404, "商家不存在");
        return summary;
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
