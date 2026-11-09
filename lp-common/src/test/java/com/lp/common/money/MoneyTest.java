package com.lp.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Verifies the yuan and fen boundary used by every amount leaving the API.
 *
 * @author Caspian
 * @since 0.4.0
 */
class MoneyTest {

    /**
     * Converts a two decimal yuan amount into exact fen.
     *
     * @author Caspian
     * @since 0.4.0
     */
    @Test
    void convertsYuanToExactFen() {
        assertThat(Money.toFen(new BigDecimal("99.00"))).isEqualTo(9900L);
        assertThat(Money.toFen(new BigDecimal("0.01"))).isEqualTo(1L);
        assertThat(Money.toFen(new BigDecimal("0"))).isZero();
        assertThat(Money.toFen(null)).isZero();
    }

    /**
     * Rejects amounts that would be silently rounded or overflow the column.
     *
     * @author Caspian
     * @since 0.4.0
     */
    @Test
    void rejectsPrecisionAndRangeViolations() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.toFen(new BigDecimal("1.005")));
        assertThatIllegalArgumentException().isThrownBy(() -> Money.toFen(new BigDecimal("-0.01")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.toFen(new BigDecimal("100000000.00")));
    }

    /**
     * Converts fen back to the stored two decimal yuan form.
     *
     * @author Caspian
     * @since 0.4.0
     */
    @Test
    void convertsFenToYuan() {
        assertThat(Money.toYuan(9900L)).isEqualByComparingTo("99.00");
        assertThat(Money.toYuan(0L)).isEqualByComparingTo("0.00");
        assertThat(Money.isStorable(Money.MAX_FEN)).isTrue();
        assertThat(Money.isStorable(Money.MAX_FEN + 1)).isFalse();
    }
}
