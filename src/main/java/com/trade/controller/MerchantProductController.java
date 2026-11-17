package com.trade.controller;

import com.trade.security.MerchantAccess;

import com.trade.common.PageResult;
import com.trade.common.Result;
import com.trade.dto.CreateProductDTO;
import com.trade.dto.UpdateProductDTO;
import com.trade.dto.UpdateProductStatusDTO;
import com.trade.entity.Product;
import com.trade.exception.BusinessException;
import com.trade.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/merchant/products")
public class MerchantProductController {

    private final ProductService productService;

    @PostMapping
    public Result<Long> createProduct(
            @RequestAttribute(value = "merchantId", required = false)
            Long merchantId,
            @Valid @RequestBody CreateProductDTO dto) {

        MerchantAccess.requireMerchant(merchantId);

        return Result.success(
                productService.createProduct(
                        merchantId,
                        dto
                )
        );
    }

    @PutMapping("/{id}")
    public Result<Void> updateProduct(
            @PathVariable Long id,
            @RequestAttribute(value = "merchantId", required = false)
            Long merchantId,
            @Valid @RequestBody UpdateProductDTO dto) {

        MerchantAccess.requireMerchant(merchantId);

        productService.updateProduct(
                merchantId,
                id,
                dto
        );

        return Result.success(null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateProductStatus(
            @PathVariable Long id,
            @RequestAttribute(value = "merchantId", required = false)
            Long merchantId,
            @Valid @RequestBody UpdateProductStatusDTO dto) {

        MerchantAccess.requireMerchant(merchantId);

        productService.updateProductStatus(
                merchantId,
                id,
                dto.getStatus()
        );

        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(
            @PathVariable Long id,
            @RequestAttribute(value = "merchantId", required = false)
            Long merchantId) {

        MerchantAccess.requireMerchant(merchantId);

        productService.deleteProduct(
                merchantId,
                id
        );

        return Result.success(null);
    }

    @GetMapping
    public Result<PageResult<Product>> getMerchantProducts(
            @RequestAttribute(value = "merchantId", required = false)
            Long merchantId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {

        MerchantAccess.requireMerchant(merchantId);

        return Result.success(
                productService.getMerchantProducts(
                        merchantId,
                        page,
                        pageSize,
                        status
                )
        );
    }
}