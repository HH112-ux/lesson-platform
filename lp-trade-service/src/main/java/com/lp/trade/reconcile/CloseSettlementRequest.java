package com.lp.trade.reconcile;

import java.time.LocalDate;

/**
 * Request body that closes one bill day.
 *
 * @param channel payment channel, defaults to 1 (Alipay) when omitted
 * @param billDate bill day in Asia/Shanghai, required
 * @author Caspian
 * @since 0.6.2
 */
public record CloseSettlementRequest(Integer channel, LocalDate billDate) {}
