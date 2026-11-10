package com.lp.trade.reconcile;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Parses the UTF-8 CSV bill that operations upload for a single bill day and bill type.
 *
 * <p>The header must be exactly {@code tradeNo,merchantOrderNo,amount,occurredAt}. Quoting is not
 * supported because the accepted columns never contain commas. Amounts are yuan with at most two
 * decimals and must be positive. {@code occurredAt} is optional and uses {@code yyyy-MM-dd
 * HH:mm:ss} in Asia/Shanghai. Blank lines are ignored; duplicated {@code tradeNo} values are
 * rejected so a bad file cannot create duplicate differences.
 *
 * @author Caspian
 * @since 0.6.1
 */
@Component
public class ReconcileBillCsvParser {

    /** Maximum accepted data rows to keep a single import bounded. */
    static final int MAX_ROWS = 50_000;

    private static final String HEADER = "tradeNo,merchantOrderNo,amount,occurredAt";

    private static final DateTimeFormatter OCCURRED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Parses the CSV text into bill lines.
     *
     * @param text raw CSV content decoded as UTF-8
     * @return parsed bill lines in file order, never empty
     * @throws BizException when the header, a row, an amount or a duplicate trade number is invalid
     * @author Caspian
     * @since 0.6.1
     */
    public List<ReconcileBillLine> parse(String text) {
        if (text == null || text.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bill file is empty");
        }
        String content = text.startsWith("\uFEFF") ? text.substring(1) : text;
        String[] lines = content.split("\n", -1);
        int headerIndex = firstNonBlank(lines, 0);
        if (headerIndex < 0 || !HEADER.equals(stripCarriageReturn(lines[headerIndex].trim()))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bill header must be " + HEADER);
        }
        List<ReconcileBillLine> result = new ArrayList<>();
        Set<String> tradeNos = new HashSet<>();
        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = stripCarriageReturn(lines[i]).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (result.size() >= MAX_ROWS) {
                throw new BizException(
                        ErrorCode.PARAM_INVALID, "bill file exceeds " + MAX_ROWS + " rows");
            }
            result.add(parseRow(line, i + 1, tradeNos));
        }
        if (result.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bill file has no data rows");
        }
        return result;
    }

    private ReconcileBillLine parseRow(String line, int lineNo, Set<String> tradeNos) {
        String[] columns = line.split(",", -1);
        if (columns.length != 4) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "bill line " + lineNo + " must have 4 columns");
        }
        String tradeNo = columns[0].trim();
        if (tradeNo.isEmpty()) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "bill line " + lineNo + " tradeNo is blank");
        }
        if (!tradeNos.add(tradeNo)) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "bill line " + lineNo + " duplicates tradeNo " + tradeNo);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(columns[2].trim());
        } catch (NumberFormatException ex) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "bill line " + lineNo + " amount is not a number");
        }
        if (amount.signum() < 0 || amount.scale() > 2) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "bill line " + lineNo + " amount must be positive with at most 2 decimals");
        }
        String occurredAtText = columns[3].trim();
        LocalDateTime occurredAt = null;
        if (!occurredAtText.isEmpty()) {
            try {
                occurredAt = LocalDateTime.parse(occurredAtText, OCCURRED_AT);
            } catch (DateTimeParseException ex) {
                throw new BizException(
                        ErrorCode.PARAM_INVALID,
                        "bill line " + lineNo + " occurredAt must be yyyy-MM-dd HH:mm:ss");
            }
        }
        String merchantOrderNo = columns[1].trim();
        return new ReconcileBillLine(
                tradeNo, merchantOrderNo.isEmpty() ? null : merchantOrderNo, amount, occurredAt);
    }

    private int firstNonBlank(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            if (!stripCarriageReturn(lines[i]).trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private String stripCarriageReturn(String value) {
        return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value;
    }
}
