package com.lp.trade.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.lp.trade.service.PaymentService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaymentControllerTest {

    @Test
    void acknowledgesWithPlainSuccessLiteral() {
        PaymentController controller = new PaymentController(mock(PaymentService.class), null);
        assertEquals(
                "success",
                controller.alipayNotify(
                        "out_trade_no=LP1&trade_no=T1&total_amount=99.00&trade_status=TRADE_SUCCESS",
                        Map.of()));
    }
}
