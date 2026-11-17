package com.trade.controller;
import com.trade.common.Result;
import com.trade.service.PaymentService;
import com.trade.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService payments;
    private final RefundService refunds;
    @PostMapping("/orders/{id}/payments")
    public Result<Map<String,String>> create(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        return Result.success(payments.checkout(userId,id));
    }
    @PostMapping(value="/payments/alipay/notify", produces="text/plain;charset=UTF-8")
    public String notifyPayment(@RequestParam Map<String,String> params) {
        try { payments.notifyPayment(params); return "success"; }
        catch (RuntimeException e) { return "failure"; }
    }
    @PostMapping("/orders/{id}/refund")
    public Result<Void> refund(@RequestAttribute("userId") Long userId,@PathVariable Long id) {
        refunds.refund(userId,id);
        return Result.success(null);
    }
}
