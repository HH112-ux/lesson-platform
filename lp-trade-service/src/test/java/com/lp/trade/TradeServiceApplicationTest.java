package com.lp.trade;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the trade service context loads without any infrastructure dependency.
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TradeServiceApplicationTest {

    /**
     * Boots the application context; an absent Nacos server must not block startup.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void contextLoads() {
    }
}
