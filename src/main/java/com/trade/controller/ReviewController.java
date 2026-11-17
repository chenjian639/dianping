package com.trade.controller;

import com.trade.common.PageResult;
import com.trade.common.Result;
import com.trade.dto.CreateReviewDTO;
import com.trade.entity.Review;
import com.trade.service.ReviewService;
import com.trade.vo.MyReviewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public Result<Long> createReview(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody CreateReviewDTO dto) {

        return Result.success(
                reviewService.createReview(
                        userId,
                        dto
                )
        );
    }

    @GetMapping("/me")
    public Result<PageResult<MyReviewVO>> getMyReviews(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "1")
            Integer page,
            @RequestParam(defaultValue = "10")
            Integer pageSize) {

        return Result.success(
                reviewService.getMyReviews(
                        userId,
                        page,
                        pageSize
                )
        );
    }
}