package com.lp.trade.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lp.common.identity.IdentityHeaders;
import com.lp.common.web.GlobalExceptionHandler;
import com.lp.trade.entity.PaymentSettleBatch;
import com.lp.trade.reconcile.ReconcilePermissions;
import com.lp.trade.reconcile.SettlementPageView;
import com.lp.trade.service.SettlementService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP layer tests for {@link SettlementController}.
 *
 * <p>The identity headers are exactly those the gateway injects after JWT verification, so these
 * tests pin the permission gate of every endpoint: {@code ADMIN} may close and void, {@code OPS}
 * may only read, and a caller without the permission code gets {@code 40301} before any service
 * call.
 */
@ExtendWith(MockitoExtension.class)
class SettlementControllerTest {

    private static final LocalDate BILL_DATE = LocalDate.of(2026, 11, 9);

    private static final String CLOSE_BODY = "{\"channel\":1,\"billDate\":\"2026-11-09\"}";

    private static final String VOID_BODY = "{\"reason\":\"duplicate bill import\"}";

    @Mock private SettlementService settlementService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new SettlementController(settlementService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void adminWithCloseCodeClosesTheDay() throws Exception {
        when(settlementService.close(1, BILL_DATE, 9001L)).thenReturn(batch(77L));

        mockMvc.perform(
                        post("/api/v1/reconcile/settlements")
                                .header(IdentityHeaders.USER_ROLES, "ADMIN")
                                .header(
                                        IdentityHeaders.USER_PERMISSIONS,
                                        ReconcilePermissions.CLOSE)
                                .header(IdentityHeaders.USER_ID, "9001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CLOSE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(77))
                .andExpect(jsonPath("$.data.netChannelAmountFen").value(7000));

        verify(settlementService).close(1, BILL_DATE, 9001L);
    }

    @Test
    void opsWithReadCodesCannotCloseTheDay() throws Exception {
        mockMvc.perform(
                        post("/api/v1/reconcile/settlements")
                                .header(IdentityHeaders.USER_ROLES, "OPS")
                                .header(
                                        IdentityHeaders.USER_PERMISSIONS,
                                        ReconcilePermissions.VIEW
                                                + ","
                                                + ReconcilePermissions.RECHECK
                                                + ","
                                                + ReconcilePermissions.HANDLE)
                                .header(IdentityHeaders.USER_ID, "9002")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CLOSE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        verify(settlementService, never()).close(any(), any(), anyLong());
    }

    @Test
    void callerWithoutPermissionCodeIsRejected() throws Exception {
        mockMvc.perform(
                        post("/api/v1/reconcile/settlements")
                                .header(IdentityHeaders.USER_ROLES, "ADMIN")
                                .header(IdentityHeaders.USER_ID, "9001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CLOSE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        verify(settlementService, never()).close(any(), any(), anyLong());
    }

    @Test
    void closeWithoutOperatorIdentityIsRejected() throws Exception {
        mockMvc.perform(
                        post("/api/v1/reconcile/settlements")
                                .header(IdentityHeaders.USER_ROLES, "ADMIN")
                                .header(
                                        IdentityHeaders.USER_PERMISSIONS,
                                        ReconcilePermissions.CLOSE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CLOSE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));

        verify(settlementService, never()).close(any(), any(), anyLong());
    }

    @Test
    void opsWithReadCodeCannotVoid() throws Exception {
        mockMvc.perform(
                        post("/api/v1/reconcile/settlements/77/void")
                                .header(IdentityHeaders.USER_ROLES, "OPS")
                                .header(IdentityHeaders.USER_PERMISSIONS, ReconcilePermissions.VIEW)
                                .header(IdentityHeaders.USER_ID, "9002")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VOID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        verify(settlementService, never()).voidSettleBatch(anyLong(), anyLong(), any(), any());
    }

    @Test
    void opsCarryingTheCloseCodeStillCannotVoid() throws Exception {
        mockMvc.perform(
                        post("/api/v1/reconcile/settlements/77/void")
                                .header(IdentityHeaders.USER_ROLES, "OPS")
                                .header(
                                        IdentityHeaders.USER_PERMISSIONS,
                                        ReconcilePermissions.CLOSE)
                                .header(IdentityHeaders.USER_ID, "9002")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VOID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        verify(settlementService, never()).voidSettleBatch(anyLong(), anyLong(), any(), any());
    }

    @Test
    void missingBodyStillReturnsTheBusinessParameterError() throws Exception {
        when(settlementService.voidSettleBatch(
                        org.mockito.ArgumentMatchers.eq(77L),
                        org.mockito.ArgumentMatchers.eq(9001L),
                        org.mockito.ArgumentMatchers.isNull(),
                        any()))
                .thenThrow(
                        new com.lp.common.exception.BizException(
                                com.lp.common.exception.ErrorCode.PARAM_INVALID,
                                "void reason is required"));

        mockMvc.perform(
                        post("/api/v1/reconcile/settlements/77/void")
                                .header(IdentityHeaders.USER_ROLES, "ADMIN")
                                .header(
                                        IdentityHeaders.USER_PERMISSIONS,
                                        ReconcilePermissions.CLOSE)
                                .header(IdentityHeaders.USER_ID, "9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void adminWithCloseCodeVoidsTheBatch() throws Exception {
        when(settlementService.voidSettleBatch(
                        org.mockito.ArgumentMatchers.eq(77L),
                        org.mockito.ArgumentMatchers.eq(9001L),
                        org.mockito.ArgumentMatchers.eq("duplicate bill import"),
                        any()))
                .thenReturn(batch(77L));

        mockMvc.perform(
                        post("/api/v1/reconcile/settlements/77/void")
                                .header(IdentityHeaders.USER_ROLES, "ADMIN")
                                .header(
                                        IdentityHeaders.USER_PERMISSIONS,
                                        ReconcilePermissions.CLOSE)
                                .header(IdentityHeaders.USER_ID, "9001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VOID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(settlementService)
                .voidSettleBatch(
                        org.mockito.ArgumentMatchers.eq(77L),
                        org.mockito.ArgumentMatchers.eq(9001L),
                        org.mockito.ArgumentMatchers.eq("duplicate bill import"),
                        any());
    }

    @Test
    void opsWithReadCodeMayListBatches() throws Exception {
        when(settlementService.page(eq(1), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new SettlementPageView(List.of(), 0, 1, 20));

        mockMvc.perform(
                        get("/api/v1/reconcile/settlements")
                                .header(IdentityHeaders.USER_ROLES, "OPS")
                                .header(
                                        IdentityHeaders.USER_PERMISSIONS,
                                        ReconcilePermissions.VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void opsWithReadCodeMayReadOneBatch() throws Exception {
        when(settlementService.get(77L)).thenReturn(batch(77L));

        mockMvc.perform(
                        get("/api/v1/reconcile/settlements/77")
                                .header(IdentityHeaders.USER_ROLES, "OPS")
                                .header(
                                        IdentityHeaders.USER_PERMISSIONS,
                                        ReconcilePermissions.VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(77))
                .andExpect(jsonPath("$.data.netLocalAmountFen").value(7000));
    }

    @Test
    void callerWithoutReadCodeCannotReadOneBatch() throws Exception {
        mockMvc.perform(
                        get("/api/v1/reconcile/settlements/77")
                                .header(IdentityHeaders.USER_ROLES, "OPS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        verify(settlementService, never()).get(anyLong());
    }

    private PaymentSettleBatch batch(long id) {
        PaymentSettleBatch batch = new PaymentSettleBatch();
        batch.setId(id);
        batch.setChannel(1);
        batch.setBillDate(BILL_DATE);
        batch.setChannelPayCount(2);
        batch.setChannelPayAmount(new BigDecimal("100.00"));
        batch.setChannelRefundCount(1);
        batch.setChannelRefundAmount(new BigDecimal("30.00"));
        batch.setLocalPayCount(2);
        batch.setLocalPayAmount(new BigDecimal("100.00"));
        batch.setLocalRefundCount(1);
        batch.setLocalRefundAmount(new BigDecimal("30.00"));
        batch.setNetChannelAmount(new BigDecimal("70.00"));
        batch.setNetLocalAmount(new BigDecimal("70.00"));
        batch.setDiffCount(0);
        batch.setDiffAmount(new BigDecimal("0.00"));
        batch.setStatus(1);
        batch.setClosedBy(9001L);
        return batch;
    }
}
