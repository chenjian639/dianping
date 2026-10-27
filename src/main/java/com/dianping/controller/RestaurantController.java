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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "餐厅接口",
        description = "餐厅查询、搜索、评价等公共接口"
)
@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final DishService dishService;
    private final ReviewService reviewService;
    @GetMapping
    public Result<List<Restaurant>> list() {
        return Result.success(restaurantService.findAll());
    }

    @Operation(
            summary = "查询餐厅详情",
            description = "根据餐厅ID查询基本信息、平均评分和评价数量"
    )
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