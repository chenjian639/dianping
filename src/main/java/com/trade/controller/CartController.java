package com.trade.controller;

import com.trade.common.Result;
import com.trade.dto.AddCartItemDTO;
import com.trade.dto.UpdateCartItemDTO;
import com.trade.service.CartService;
import com.trade.vo.CartItemVO;
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