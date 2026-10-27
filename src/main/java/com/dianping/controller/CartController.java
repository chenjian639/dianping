package com.dianping.controller;

import com.dianping.common.Result;
import com.dianping.dto.AddCartItemDTO;
import com.dianping.dto.UpdateCartItemDTO;
import com.dianping.service.CartService;
import com.dianping.vo.CartItemVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    @PostMapping("/items")
    public Result<Void> addItem(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody AddCartItemDTO dto) {

        cartService.addItem(userId, dto);

        return Result.success(null);
    }

    @GetMapping
    public Result<List<CartItemVO>> getCart(
            @RequestAttribute("userId") Long userId) {

        return Result.success(cartService.getCart(userId));
    }

    @PutMapping("/items/{id}")
    public Result<Void> updateQuantity(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCartItemDTO dto) {

        cartService.updateQuantity(
                userId,
                id,
                dto
        );

        return Result.success(null);
    }

    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id) {

        cartService.deleteItem(userId, id);

        return Result.success(null);
    }
}