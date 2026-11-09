package com.lp.common.money;

import java.math.BigDecimal;

/**
 * Converts between yuan, the storage unit, and fen, the external unit.
 *
 * <p>The database keeps every amount as {@code DECIMAL(10,2)} yuan. Public and console APIs expose
 * fen so clients never parse decimal strings, and every conversion goes through this class instead
 * of being repeated in controllers or services.
 *
 * <p>{@link #MAX_FEN} is the largest value that fits {@code DECIMAL(10,2)}; a larger amount would
 * overflow the column, so it is rejected before reaching the database.
 *
 * @author Caspian
 * @since 0.4.0
 */
public final class Money {

    /** Largest amount in fen that fits {@code DECIMAL(10,2)} yuan. */
    public static final long MAX_FEN = 9_999_999_999L;

    private static final int FEN_SCALE = 2;

    private Money() {}

    /**
     * Converts a stored yuan amount to fen.
     *
     * @param yuan amount in yuan, {@code null} is treated as zero
     * @return amount in fen
     * @throws IllegalArgumentException when the amount is negative, has more than two decimals or
     *     exceeds {@link #MAX_FEN}
     * @author Caspian
     * @since 0.4.0
     */
    public static long toFen(BigDecimal yuan) {
        if (yuan == null) {
            return 0L;
        }
        if (yuan.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (yuan.stripTrailingZeros().scale() > FEN_SCALE) {
            throw new IllegalArgumentException("amount must have at most two decimals");
        }
        long fen = yuan.movePointRight(FEN_SCALE).longValueExact();
        if (fen > MAX_FEN) {
            throw new IllegalArgumentException("amount exceeds the maximum");
        }
        return fen;
    }

    /**
     * Converts fen back to the stored yuan amount.
     *
     * @param fen amount in fen
     * @return amount in yuan with two decimals
     * @throws IllegalArgumentException when the amount is negative or exceeds {@link #MAX_FEN}
     * @author Caspian
     * @since 0.4.0
     */
    public static BigDecimal toYuan(long fen) {
        if (fen < 0 || fen > MAX_FEN) {
            throw new IllegalArgumentException("amount is out of range");
        }
        return BigDecimal.valueOf(fen, FEN_SCALE);
    }

    /**
     * Tells whether an amount in fen can be stored without overflowing the column.
     *
     * @param fen amount in fen
     * @return {@code true} when the amount is between zero and {@link #MAX_FEN} inclusive
     * @author Caspian
     * @since 0.4.0
     */
    public static boolean isStorable(long fen) {
        return fen >= 0 && fen <= MAX_FEN;
    }
}
