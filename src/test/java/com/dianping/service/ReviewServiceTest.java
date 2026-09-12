package com.dianping.service;

import com.dianping.dto.CreateReviewDTO;
import com.dianping.entity.Order;
import com.dianping.entity.Review;
import com.dianping.enums.OrderStatus;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.OrderMapper;
import com.dianping.mapper.RestaurantMapper;
import com.dianping.mapper.ReviewMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RestaurantMapper restaurantMapper;

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
        order.setRestaurantId(1L);
        order.setStatus(
                OrderStatus.COMPLETED.getCode()
        );

        when(orderMapper.selectCompletedOrderByUser(
                orderId,
                userId,
                OrderStatus.COMPLETED.getCode()
        )).thenReturn(order);

        when(reviewMapper.countByOrderId(orderId))
                .thenReturn(0);

        when(reviewMapper.insert(any(Review.class)))
                .thenAnswer(invocation -> {
                    Review review =
                            invocation.getArgument(0);
                    review.setId(200L);
                    return 1;
                });

        when(restaurantMapper.addReviewStatistics(
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
                savedReview.getRestaurantId()
        );

        assertEquals(
                5,
                savedReview.getRating()
        );

        assertEquals(
                "很好吃",
                savedReview.getContent()
        );

        verify(restaurantMapper)
                .addReviewStatistics(
                        1L,
                        5
                );

        verify(stringRedisTemplate)
                .delete(
                        "restaurant:detail:1"
                );
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
        order.setRestaurantId(1L);
        order.setStatus(
                OrderStatus.COMPLETED.getCode()
        );

        when(orderMapper.selectCompletedOrderByUser(
                orderId,
                userId,
                OrderStatus.COMPLETED.getCode()
        )).thenReturn(order);

        when(reviewMapper.countByOrderId(orderId))
                .thenReturn(1);

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
}
