package com.dianping.controller;
import com.dianping.common.Result;
import com.dianping.entity.Payment;
import com.dianping.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
@RestController
@RequiredArgsConstructor
public class PaymentController {
    @GetMapping("/admin/mock-payments")
    public Result<java.util.List<com.dianping.vo.AdminPaymentVO>> pending() {
        return Result.success(service.pendingForAdmin());
    }
    private final PaymentService service;
    public record Callback(@NotBlank @Size(max=64) String channelReference,
                           @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal amount) {}
    @PostMapping("/orders/{id}/payments")
    public Result<Payment> create(@RequestAttribute("userId") Long userId,@PathVariable Long id) {
        return Result.success(service.create(userId,id));
    }
    @PostMapping("/admin/mock-payments/{id}/callback")
    public Result<Void> callback(@PathVariable Long id,@Valid @RequestBody Callback body) {
        service.callback(id,body.channelReference(),body.amount());
        return Result.success(null);
    }
    @PostMapping("/orders/{id}/refund")
    public Result<Void> refund(@RequestAttribute("userId") Long userId,@PathVariable Long id) {
        service.refund(userId,id);
        return Result.success(null);
    }
}
