package com.dianping.controller;

import com.dianping.security.MerchantAccess;

import com.dianping.common.PageResult;
import com.dianping.common.Result;
import com.dianping.dto.CreateProductDTO;
import com.dianping.dto.UpdateProductDTO;
import com.dianping.dto.UpdateProductStatusDTO;
import com.dianping.entity.Product;
import com.dianping.exception.BusinessException;
import com.dianping.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/merchant/products")
public class MerchantProductController {

    private final ProductService productService;

    @PostMapping
    public Result<Long> createProduct(
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @Valid @RequestBody CreateProductDTO dto) {

        MerchantAccess.requireRestaurant(restaurantId);

        return Result.success(
                productService.createProduct(
                        restaurantId,
                        dto
                )
        );
    }

    @PutMapping("/{id}")
    public Result<Void> updateProduct(
            @PathVariable Long id,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @Valid @RequestBody UpdateProductDTO dto) {

        MerchantAccess.requireRestaurant(restaurantId);

        productService.updateProduct(
                restaurantId,
                id,
                dto
        );

        return Result.success(null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateProductStatus(
            @PathVariable Long id,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @Valid @RequestBody UpdateProductStatusDTO dto) {

        MerchantAccess.requireRestaurant(restaurantId);

        productService.updateProductStatus(
                restaurantId,
                id,
                dto.getStatus()
        );

        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(
            @PathVariable Long id,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId) {

        MerchantAccess.requireRestaurant(restaurantId);

        productService.deleteProduct(
                restaurantId,
                id
        );

        return Result.success(null);
    }

    @GetMapping
    public Result<PageResult<Product>> getMerchantProducts(
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {

        MerchantAccess.requireRestaurant(restaurantId);

        return Result.success(
                productService.getMerchantProducts(
                        restaurantId,
                        page,
                        pageSize,
                        status
                )
        );
    }
}