package com.dianping.controller;

import com.dianping.common.PageResult;
import com.dianping.common.Result;
import com.dianping.dto.CreateReviewDTO;
import com.dianping.entity.Review;
import com.dianping.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

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
}