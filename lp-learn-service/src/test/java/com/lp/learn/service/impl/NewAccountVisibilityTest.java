package com.lp.learn.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.lp.learn.service.EntitlementService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pins the learning half of AC-16: after the same WeChat openid is bound to a new account, the
 * entitlement query that guards playback sees nothing granted to the old account.
 *
 * <p>Rebinding keeps every {@code user_entitlement} row on the old user id, so the new id must come
 * back without the course even though both accounts belong to one person.
 *
 * @author Caspian
 * @since 0.3.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class NewAccountVisibilityTest {

    private static final long OLD_USER_ID = 2001L;

    private static final long NEW_USER_ID = 2002L;

    private static final long COURSE_ID = 3001L;

    @Autowired private EntitlementService entitlementService;

    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * Recreates the entitlement table with one active grant for the old account.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @BeforeEach
    void prepareSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS user_entitlement");
        jdbcTemplate.execute(
                "CREATE TABLE user_entitlement ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "user_id BIGINT NOT NULL,"
                        + "course_id BIGINT NOT NULL,"
                        + "order_id BIGINT,"
                        + "status TINYINT NOT NULL DEFAULT 0,"
                        + "granted_at DATETIME,"
                        + "expire_at DATETIME,"
                        + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.update(
                "INSERT INTO user_entitlement (user_id, course_id, status, granted_at, deleted) "
                        + "VALUES (?, ?, 0, CURRENT_TIMESTAMP, 0)",
                OLD_USER_ID,
                COURSE_ID);
    }

    /**
     * Asks the same questions the play guard and my-courses ask, once per account.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void newAccountOfTheSameOpenidSeesNoEntitlementsOfTheOldOne() {
        assertThat(entitlementService.hasEntitlement(OLD_USER_ID, COURSE_ID)).isTrue();
        assertThat(entitlementService.hasEntitlement(NEW_USER_ID, COURSE_ID)).isFalse();
        assertThat(entitlementService.ownedCourseIds(NEW_USER_ID, List.of(COURSE_ID))).isEmpty();
    }
}
