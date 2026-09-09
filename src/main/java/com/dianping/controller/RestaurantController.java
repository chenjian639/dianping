package com.dianping.controller;

import com.dianping.common.PageResult;
import com.dianping.common.Result;
import com.dianping.entity.Dish;
import com.dianping.entity.Restaurant;
import com.dianping.service.DishService;
import com.dianping.service.RestaurantService;
import com.dianping.service.ReviewService;
import com.dianping.vo.RestaurantDetailVO;
import com.dianping.vo.RestaurantReviewSummaryVO;
import com.dianping.vo.RestaurantSearchVO;
import com.dianping.vo.ReviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restaurants")
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;
    @Autowired
    private DishService dishService;
    @Autowired
    private ReviewService reviewService;
    @GetMapping
    public Result<List<Restaurant>> list() {
        return Result.success(restaurantService.findAll());
    }

    @GetMapping("/{id}")
    public Result<RestaurantDetailVO> getRestaurantById(
            @PathVariable Long id) {

        return Result.success(
                restaurantService.getRestaurantDetail(id)
        );
    }

    @GetMapping("/{id}/dishes")
    public Result<List<Dish>> getDishes(@PathVariable Long id) {
        return Result.success(dishService.findByRestaurantId(id));
    }

    @GetMapping("/{id}/reviews")
    public Result<PageResult<ReviewVO>> getReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        return Result.success(
                reviewService.getRestaurantReviews(
                        id,
                        page,
                        pageSize
                )
        );
    }

    @GetMapping("/{id}/review-summary")
    public Result<RestaurantReviewSummaryVO> getReviewSummary(
            @PathVariable Long id) {

        return Result.success(
                reviewService.getRestaurantReviewSummary(id)
        );
    }

    @GetMapping("/search")
    public Result<PageResult<RestaurantSearchVO>> searchRestaurants(
            @RequestParam(required = false)
            String keyword,

            @RequestParam(defaultValue = "latest")
            String sort,

            @RequestParam(defaultValue = "1")
            Integer page,

            @RequestParam(defaultValue = "10")
            Integer pageSize) {

        return Result.success(
                restaurantService.searchRestaurants(
                        keyword,
                        sort,
                        page,
                        pageSize
                )
        );
    }
}