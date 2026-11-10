package com.lp.auth.job;

import static com.lp.auth.job.AccountCancellationExecutionJob.METRIC_EXECUTED;
import static com.lp.auth.job.AccountCancellationExecutionJob.METRIC_SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lp.auth.client.AccountAnonymizationClient;
import com.lp.auth.client.AccountDeletionBlockerClient;
import com.lp.auth.config.AccountDeletionProperties;
import com.lp.auth.dto.AccountDeletionBlockerView;
import com.lp.auth.mapper.AccountAnonymizationMapper;
import com.lp.auth.mapper.UserAccountCancellationMapper;
import com.lp.auth.service.AccountAnonymizationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves that two independent Spring contexts sharing one database claim a due cancellation once.
 *
 * <p>Scope, stated explicitly: this is <strong>one JVM with two application contexts</strong>, not
 * a cross-container or multi-replica test. It exercises the production claim statement ({@code
 * UPDATE ... WHERE id = ? AND status = 0}) on the real mapper, so the only thing it cannot
 * reproduce is separate database connections from separate hosts.
 *
 * <p>Database caveat: the test runs on H2 in MySQL mode because CI provisions no MySQL service. H2
 * re-evaluates the {@code status = 0} predicate after the row lock is released, which is the MySQL
 * behaviour the design relies on, but the follow-up manual run against two real service processes
 * on MySQL is recorded in the PR body as the authoritative cross-process evidence.
 *
 * @author Caspian
 * @since 0.3.0
 */
class AccountCancellationClaimConcurrencyTest {

    private static final String DB_URL =
            "jdbc:h2:mem:lp_ums_claim;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;"
                    + "LOCK_TIMEOUT=10000";

    private static final long USER_ID = 10L;

    private static final long REQUEST_ID = 1L;

    private static final String SALT = "claim-concurrency-salt";

    private static final String ORIGINAL_OPENID = "wx-openid-10";

    private static GenericApplicationContext firstContext;

    private static GenericApplicationContext secondContext;

    private static SimpleMeterRegistry firstMetrics;

    private static SimpleMeterRegistry secondMetrics;

    /**
     * Creates the schema, seeds one due request and boots two separate contexts on that database.
     *
     * @throws Exception when the schema or a context cannot be prepared
     * @author Caspian
     * @since 0.3.0
     */
    @BeforeAll
    static void prepareDatabaseAndContexts() throws Exception {
        seedDatabase();
        firstMetrics = new SimpleMeterRegistry();
        secondMetrics = new SimpleMeterRegistry();
        firstContext = newContext(firstMetrics);
        secondContext = newContext(secondMetrics);
    }

    /**
     * Closes both contexts so their connection pools do not leak into other test classes.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @AfterAll
    static void closeContexts() {
        if (firstContext != null) {
            firstContext.close();
        }
        if (secondContext != null) {
            secondContext.close();
        }
    }

    /**
     * Runs both jobs at the same instant and checks that exactly one claims and anonymises.
     *
     * <p>Asserts the single winner, the metrics split (one executed, one skipped) and that the
     * account and binding rows are written once without a unique key violation.
     *
     * @throws Exception when a job throws instead of returning a result
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void onlyOneContextClaimsAndAnonymisesTheRequest() throws Exception {
        AccountCancellationExecutionJob firstJob =
                firstContext.getBean(AccountCancellationExecutionJob.class);
        AccountCancellationExecutionJob secondJob =
                secondContext.getBean(AccountCancellationExecutionJob.class);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstRun =
                    pool.submit(
                            () -> {
                                start.await();
                                return firstJob.runOnce();
                            });
            Future<Integer> secondRun =
                    pool.submit(
                            () -> {
                                start.await();
                                return secondJob.runOnce();
                            });
            start.countDown();
            int firstExecuted = firstRun.get(30, TimeUnit.SECONDS);
            int secondExecuted = secondRun.get(30, TimeUnit.SECONDS);

            assertThat(firstExecuted + secondExecuted).isEqualTo(1);

            SimpleMeterRegistry winner = firstExecuted == 1 ? firstMetrics : secondMetrics;
            SimpleMeterRegistry loser = firstExecuted == 1 ? secondMetrics : firstMetrics;
            assertThat(winner.counter(METRIC_EXECUTED).count()).isEqualTo(1.0);
            assertThat(loser.counter(METRIC_EXECUTED).count()).isZero();
            assertThat(loser.counter(METRIC_SKIPPED, "userId", String.valueOf(USER_ID)).count())
                    .isEqualTo(1.0);
            assertThat(winner.counter(METRIC_SKIPPED, "userId", String.valueOf(USER_ID)).count())
                    .isZero();
        } finally {
            pool.shutdownNow();
        }

        assertThat(value("SELECT status FROM user_account_cancellation WHERE id = " + REQUEST_ID))
                .isEqualTo("2");
        assertThat(
                        value(
                                "SELECT executed_at FROM user_account_cancellation WHERE id = "
                                        + REQUEST_ID))
                .isNotNull();
        assertThat(value("SELECT status FROM sys_user WHERE id = " + USER_ID)).isEqualTo("3");
        assertThat(value("SELECT username FROM sys_user WHERE id = " + USER_ID))
                .isEqualTo("deleted_" + USER_ID);
        assertThat(value("SELECT nickname FROM sys_user WHERE id = " + USER_ID)).isEqualTo("已注销用户");
        assertThat(value("SELECT email FROM sys_user WHERE id = " + USER_ID)).isNull();
        assertThat(value("SELECT phone FROM sys_user WHERE id = " + USER_ID)).isNull();
        assertThat(value("SELECT avatar_url FROM sys_user WHERE id = " + USER_ID)).isNull();
        assertThat(value("SELECT password_hash FROM sys_user WHERE id = " + USER_ID))
                .isNotEqualTo("original-hash")
                .startsWith("$2");
        assertThat(value("SELECT COUNT(*) FROM sys_user WHERE id = " + USER_ID)).isEqualTo("1");
        assertThat(value("SELECT COUNT(*) FROM sys_user_wechat WHERE user_id = " + USER_ID))
                .isEqualTo("1");
        assertThat(value("SELECT openid FROM sys_user_wechat WHERE user_id = " + USER_ID))
                .startsWith("anon:")
                .hasSize(69);
        assertThat(value("SELECT unionid FROM sys_user_wechat WHERE user_id = " + USER_ID))
                .isNull();
        assertThat(value("SELECT session_key FROM sys_user_wechat WHERE user_id = " + USER_ID))
                .isNull();
        assertThat(value("SELECT deleted FROM sys_user_wechat WHERE user_id = " + USER_ID))
                .isEqualTo("1");
    }

    /**
     * Builds one context with its own data source, mappers, transaction manager, metrics and job.
     *
     * <p>Both contexts point at the same in-memory database, which is what makes the claim
     * contention real while keeping the two bean graphs fully independent.
     *
     * @param meterRegistry registry owned by the context, so the executed count is per replica
     * @return refreshed context exposing a job wired with the production mapper statements
     * @author Caspian
     * @since 0.3.0
     */
    private static GenericApplicationContext newContext(SimpleMeterRegistry meterRegistry) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(DataSource.class, () -> new DriverManagerDataSource(DB_URL, "sa", ""));
        context.registerBean(
                SqlSessionFactory.class,
                () -> {
                    SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
                    factory.setDataSource(context.getBean(DataSource.class));
                    try {
                        SqlSessionFactory sessionFactory = factory.getObject();
                        sessionFactory
                                .getConfiguration()
                                .addMapper(UserAccountCancellationMapper.class);
                        sessionFactory
                                .getConfiguration()
                                .addMapper(AccountAnonymizationMapper.class);
                        return sessionFactory;
                    } catch (Exception exception) {
                        throw new IllegalStateException(
                                "cannot build the test SqlSessionFactory", exception);
                    }
                });
        context.registerBean(
                SqlSessionTemplate.class,
                () -> new SqlSessionTemplate(context.getBean(SqlSessionFactory.class)));
        context.registerBean(
                UserAccountCancellationMapper.class,
                () ->
                        context.getBean(SqlSessionTemplate.class)
                                .getMapper(UserAccountCancellationMapper.class));
        context.registerBean(
                AccountAnonymizationMapper.class,
                () ->
                        context.getBean(SqlSessionTemplate.class)
                                .getMapper(AccountAnonymizationMapper.class));
        context.registerBean(
                DataSourceTransactionManager.class,
                () -> new DataSourceTransactionManager(context.getBean(DataSource.class)));
        context.registerBean(
                TransactionTemplate.class,
                () -> new TransactionTemplate(context.getBean(DataSourceTransactionManager.class)));
        context.registerBean(
                AccountDeletionProperties.class,
                AccountCancellationClaimConcurrencyTest::properties);
        context.registerBean(
                AccountAnonymizationService.class,
                () ->
                        new AccountAnonymizationService(
                                context.getBean(AccountAnonymizationMapper.class),
                                mock(AccountAnonymizationClient.class),
                                context.getBean(AccountDeletionProperties.class),
                                new BCryptPasswordEncoder()));
        context.registerBean(
                AccountDeletionBlockerClient.class,
                () -> {
                    AccountDeletionBlockerClient client = mock(AccountDeletionBlockerClient.class);
                    when(client.blockers(USER_ID))
                            .thenReturn(new AccountDeletionBlockerView(USER_ID, 0, 0, false));
                    return client;
                });
        context.registerBean(SimpleMeterRegistry.class, () -> meterRegistry);
        context.registerBean(
                AccountCancellationExecutionJob.class,
                () ->
                        new AccountCancellationExecutionJob(
                                context.getBean(UserAccountCancellationMapper.class),
                                context.getBean(AccountDeletionBlockerClient.class),
                                context.getBean(AccountAnonymizationService.class),
                                context.getBean(AccountDeletionProperties.class),
                                context.getBean(TransactionTemplate.class),
                                context.getBean(SimpleMeterRegistry.class)));
        context.refresh();
        return context;
    }

    private static AccountDeletionProperties properties() {
        AccountDeletionProperties properties = new AccountDeletionProperties();
        properties.setAnonymizeSalt(SALT);
        return properties;
    }

    private static void seedDatabase() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        try (Connection connection = DriverManager.getConnection(DB_URL, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE ALIAS IF NOT EXISTS SHA2 FOR"
                            + " \"com.lp.auth.support.Sha2Alias.sha2\"");
            statement.execute(
                    "CREATE TABLE sys_user (id BIGINT PRIMARY KEY, username VARCHAR(32) NOT NULL,"
                            + " password_hash VARCHAR(100) NOT NULL, nickname VARCHAR(32),"
                            + " avatar_url VARCHAR(255), email VARCHAR(64), phone VARCHAR(20),"
                            + " gender TINYINT NOT NULL DEFAULT 0,"
                            + " status TINYINT NOT NULL DEFAULT 1,"
                            + " deleted TINYINT NOT NULL DEFAULT 0,"
                            + " created_at DATETIME, updated_at DATETIME)");
            statement.execute(
                    "CREATE TABLE sys_user_wechat (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                            + " user_id BIGINT NOT NULL, openid VARCHAR(128) NOT NULL,"
                            + " unionid VARCHAR(128), session_key VARCHAR(255),"
                            + " deleted TINYINT NOT NULL DEFAULT 0,"
                            + " created_at DATETIME, updated_at DATETIME)");
            statement.execute("CREATE UNIQUE INDEX uk_openid ON sys_user_wechat (openid)");
            statement.execute(
                    "CREATE TABLE user_account_cancellation (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                            + " user_id BIGINT NOT NULL,"
                            + " status TINYINT NOT NULL DEFAULT 0,"
                            + " requested_at DATETIME NOT NULL,"
                            + " effective_at DATETIME NOT NULL,"
                            + " withdrawn_at DATETIME, executed_at DATETIME,"
                            + " created_at DATETIME, updated_at DATETIME)");
            statement.execute(
                    "INSERT INTO sys_user (id, username, password_hash, nickname, status, deleted)"
                            + " VALUES ("
                            + USER_ID
                            + ", 'claim-user', 'original-hash', 'Claim User', 2, 0)");
            statement.execute(
                    "INSERT INTO sys_user_wechat (user_id, openid, unionid, session_key, deleted)"
                            + " VALUES ("
                            + USER_ID
                            + ", '"
                            + ORIGINAL_OPENID
                            + "', 'wx-unionid-10', 'session-key-10', 0)");
            statement.execute(
                    "INSERT INTO user_account_cancellation"
                            + " (id, user_id, status, requested_at, effective_at)"
                            + " VALUES ("
                            + REQUEST_ID
                            + ", "
                            + USER_ID
                            + ", 0, '"
                            + now.minusDays(8)
                            + "', '"
                            + now.minusDays(1)
                            + "')");
        }
    }

    private static String value(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(DB_URL, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }
}
