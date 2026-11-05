package com.lp.trade;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the trade service context loads against an in-memory H2 database.
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TradeServiceApplicationTest {

    /**
     * Boots the application context; an absent Nacos server must not block startup.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void contextLoads() {}
}
