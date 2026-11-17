package com.trade.controller;

import com.trade.common.PageResult;
import com.trade.common.Result;
import com.trade.entity.Product;
import com.trade.entity.Merchant;
import com.trade.service.ProductService;
import com.trade.service.MerchantService;
import com.trade.service.ReviewService;
import com.trade.vo.MerchantDetailVO;
import com.trade.vo.MerchantReviewSummaryVO;
import com.trade.vo.MerchantSearchVO;
import com.trade.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "商家接口",
        description = "商家查询、搜索、评价等公共接口"
)
@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/merchants")
public class MerchantController {

    private final MerchantService merchantService;
    private final ProductService productService;
    private final ReviewService reviewService;
    @GetMapping
    public Result<List<Merchant>> list() {
        return Result.success(merchantService.findAll());
    }

    @Operation(
            summary = "查询商家详情",
            description = "根据商家ID查询基本信息、平均评分和评价数量"
    )
    @GetMapping("/{id}")
    public Result<MerchantDetailVO> getMerchantById(
            @PathVariable Long id) {

        return Result.success(
                merchantService.getMerchantDetail(id)
        );
    }

    @GetMapping("/{id}/products")
    public Result<List<Product>> getProducts(@PathVariable Long id) {
        return Result.success(productService.findByMerchantId(id));
    }

    @GetMapping("/{id}/reviews")
    public Result<PageResult<ReviewVO>> getReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        return Result.success(
                reviewService.getMerchantReviews(
                        id,
                        page,
                        pageSize
                )
        );
    }

    @GetMapping("/{id}/review-summary")
    public Result<MerchantReviewSummaryVO> getReviewSummary(
            @PathVariable Long id) {

        return Result.success(
                reviewService.getMerchantReviewSummary(id)
        );
    }

    @GetMapping("/search")
    public Result<PageResult<MerchantSearchVO>> searchMerchants(
            @RequestParam(required = false)
            String keyword,

            @RequestParam(defaultValue = "latest")
            String sort,

            @RequestParam(defaultValue = "1")
            Integer page,

            @RequestParam(defaultValue = "10")
            Integer pageSize) {

        return Result.success(
                merchantService.searchMerchants(
                        keyword,
                        sort,
                        page,
                        pageSize
                )
        );
    }
}