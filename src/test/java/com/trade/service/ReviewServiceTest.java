package com.trade.service;

import com.trade.dto.CreateReviewDTO;
import com.trade.entity.Order;
import com.trade.entity.Review;
import com.trade.enums.OrderStatus;
import com.trade.exception.BusinessException;
import com.trade.mapper.OrderMapper;
import com.trade.mapper.MerchantMapper;
import com.trade.mapper.ReviewMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private MerchantMapper merchantMapper;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void createReview_shouldSuccess_whenOrderCompletedAndNotReviewed() {

        Long userId = 3L;
        Long orderId = 100L;

        CreateReviewDTO dto = new CreateReviewDTO();
        dto.setOrderId(orderId);
        dto.setRating(5);
        dto.setContent("很好吃");

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setMerchantId(1L);
        order.setStatus(
                OrderStatus.COMPLETED.getCode()
        );

        when(orderMapper.selectCompletedOrderByUser(
                orderId,
                userId,
                OrderStatus.COMPLETED.getCode()
        )).thenReturn(order);

        when(reviewMapper.insert(any(Review.class)))
                .thenAnswer(invocation -> {
                    Review review =
                            invocation.getArgument(0);
                    review.setId(200L);
                    return 1;
                });

        when(merchantMapper.addReviewStatistics(
                1L,
                5
        )).thenReturn(1);

        Long reviewId =
                reviewService.createReview(
                        userId,
                        dto
                );

        assertEquals(200L, reviewId);

        ArgumentCaptor<Review> captor =
                ArgumentCaptor.forClass(
                        Review.class
                );

        verify(reviewMapper)
                .insert(captor.capture());

        Review savedReview =
                captor.getValue();

        assertEquals(
                userId,
                savedReview.getUserId()
        );

        assertEquals(
                orderId,
                savedReview.getOrderId()
        );

        assertEquals(
                1L,
                savedReview.getMerchantId()
        );

        assertEquals(
                5,
                savedReview.getRating()
        );

        assertEquals(
                "很好吃",
                savedReview.getContent()
        );

        verify(merchantMapper)
                .addReviewStatistics(
                        1L,
                        5
                );

        verify(outboxEventService).merchantChanged(1L);
    }

    @Test
    void createReview_shouldThrowException_whenOrderNotCompleted() {

        Long userId = 3L;
        Long orderId = 100L;

        CreateReviewDTO dto =
                new CreateReviewDTO();

        dto.setOrderId(orderId);
        dto.setRating(5);
        dto.setContent("很好吃");

        when(orderMapper.selectCompletedOrderByUser(
                orderId,
                userId,
                OrderStatus.COMPLETED.getCode()
        )).thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> reviewService.createReview(
                        userId,
                        dto
                )
        );

        verify(
                reviewMapper,
                never()
        ).insert(any());
    }

    @Test
    void createReview_shouldThrowException_whenAlreadyReviewed() {

        Long userId = 3L;
        Long orderId = 100L;

        CreateReviewDTO dto =
                new CreateReviewDTO();

        dto.setOrderId(orderId);
        dto.setRating(5);
        dto.setContent("很好吃");

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setMerchantId(1L);
        order.setStatus(
                OrderStatus.COMPLETED.getCode()
        );

        when(orderMapper.selectCompletedOrderByUser(
                orderId,
                userId,
                OrderStatus.COMPLETED.getCode()
        )).thenReturn(order);

        // 唯一约束冲突由 DB 抛 DuplicateKeyException
        when(reviewMapper.insert(any(Review.class)))
                .thenThrow(new DuplicateKeyException("uk_order_id"));

        assertThrows(
                BusinessException.class,
                () -> reviewService.createReview(
                        userId,
                        dto
                )
        );

        // 重复评价时不应再更新评分统计
        verify(
                merchantMapper,
                never()
        ).addReviewStatistics(anyLong(), anyInt());
    }
}
