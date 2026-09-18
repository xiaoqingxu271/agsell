package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.service.PaymentService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "支付接口", description = "模拟支付、查询支付状态")
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "创建支付（模拟）")
    @PostMapping("/create")
    public BaseResponse<Void> createPayment(@RequestBody java.util.Map<String, String> request) {
        String orderNo = request.get("orderNo");
        Integer payType = null;
        String payTypeStr = request.get("payType");
        if (payTypeStr != null && !payTypeStr.isBlank()) {
            try {
                payType = Integer.valueOf(payTypeStr);
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "支付方式参数不合法");
            }
        }
        paymentService.createPayment(orderNo, payType);
        return ResultUtils.success(null);
    }

    @Operation(summary = "查询支付状态")
    @GetMapping("/{orderNo}")
    public BaseResponse<PaymentService.PaymentStatusVO> getPaymentStatus(@PathVariable String orderNo) {
        return ResultUtils.success(paymentService.getPaymentStatus(orderNo));
    }
}
