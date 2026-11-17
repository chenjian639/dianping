package com.trade.service;

import com.alipay.api.*;
import com.alipay.api.domain.*;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.trade.config.AlipayProperties;
import com.trade.entity.Payment;
import com.trade.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlipayGateway {
    private final AlipayProperties properties;

    private AlipayClient client() {
        for (String value : new String[]{properties.getAppId(), properties.getSellerId(), properties.getPrivateKey(),
                properties.getPublicKey(), properties.getNotifyUrl(), properties.getReturnUrl()}) {
            if (value == null || value.isBlank()) throw new BusinessException("支付宝尚未配置，请联系商家");
        }
        return new DefaultAlipayClient(properties.getGateway(), properties.getAppId(), properties.getPrivateKey(),
                "json", "UTF-8", properties.getPublicKey(), "RSA2");
    }

    public String page(Payment payment, LocalDateTime expiresAt) {
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(tradeNo(payment));
        model.setTotalAmount(payment.getAmount().toPlainString());
        model.setSubject("商品订单 " + payment.getOrderId());
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        model.setTimeExpire(expiresAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setBizModel(model);
        request.setNotifyUrl(properties.getNotifyUrl());
        request.setReturnUrl(properties.getReturnUrl());
        try { return client().pageExecute(request, "GET").getBody(); }
        catch (AlipayApiException e) { throw unavailable(); }
    }

    public boolean verify(Map<String,String> params) {
        if (!"RSA2".equals(params.get("sign_type")) || properties.getAppId() == null
                || !properties.getAppId().equals(params.get("app_id"))
                || properties.getSellerId() == null || !properties.getSellerId().equals(params.get("seller_id"))) return false;
        try { return AlipaySignature.rsaCheckV1(params, properties.getPublicKey(), "UTF-8", "RSA2"); }
        catch (AlipayApiException | RuntimeException e) { return false; }
    }

    public AlipayTradeQueryResponse query(Payment payment) {
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(tradeNo(payment));
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        try {
            var response = client().execute(request);
            if (!response.isSuccess() && !"ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) throw unavailable();
            return response;
        } catch (AlipayApiException e) { throw unavailable(); }
    }

    public void close(Payment payment, boolean expired) {
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(tradeNo(payment));
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizModel(model);
        try {
            var response = client().execute(request);
            // A missing trade can still be created by an already issued payment URL.
            // Do not release inventory until the URL's expiry has passed and remote close is confirmed.
            if (!response.isSuccess() && !(expired && "ACQ.TRADE_NOT_EXIST".equals(response.getSubCode()))) throw new BusinessException("支付渠道尚未确认关单，请稍后重试");
        } catch (AlipayApiException e) { throw unavailable(); }
    }

    public void refund(Payment payment) {
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(tradeNo(payment));
        model.setOutRequestNo("refund-" + payment.getId());
        model.setRefundAmount(payment.getAmount().toPlainString());
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizModel(model);
        try {
            if (!client().execute(request).isSuccess()) throw new BusinessException("支付宝退款未确认，请稍后重试");
        } catch (AlipayApiException e) { throw unavailable(); }
    }

    public static String tradeNo(Payment payment) { return "trade-" + payment.getId(); }
    private BusinessException unavailable() { return new BusinessException("支付宝暂不可用，请稍后重试"); }
}
