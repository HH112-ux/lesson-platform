package com.lp.learn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the learn service context loads with an in-memory database.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LearnServiceApplicationTest {

    /**
     * Boots the application context; an absent Nacos server must not block startup.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void contextLoads() {}
}
