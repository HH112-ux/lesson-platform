package com.lp.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.auth.client.AccountAnonymizationClient;
import com.lp.auth.config.AccountDeletionProperties;
import com.lp.auth.config.MiniappProperties;
import com.lp.auth.constant.UserStatus;
import com.lp.auth.dto.LoginRequest;
import com.lp.auth.dto.LoginResponse;
import com.lp.auth.dto.LogoutRequest;
import com.lp.auth.dto.MeResponse;
import com.lp.auth.dto.MiniappLoginRequest;
import com.lp.auth.dto.MiniappLoginResponse;
import com.lp.auth.dto.RefreshRequest;
import com.lp.auth.dto.RefreshResponse;
import com.lp.auth.dto.RegisterRequest;
import com.lp.auth.entity.UserAccount;
import com.lp.auth.entity.UserWechat;
import com.lp.auth.mapper.AccountAnonymizationMapper;
import com.lp.auth.mapper.AuthUserMapper;
import com.lp.auth.security.JwtTokenService;
import com.lp.auth.security.RefreshSession;
import com.lp.auth.security.RefreshTokenStore;
import com.lp.auth.security.WechatMiniappClient;
import com.lp.auth.security.WechatSession;
import com.lp.auth.service.AccountAnonymizationService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

/**
 * Covers the login, refresh and logout decision branches without Redis.
 *
 * <p>The token issuer is real and signs with a generated RSA key, while persistence and the refresh
 * whitelist are in-memory stand-ins, so the asserted tokens are genuinely verifiable and the
 * rotation rules are observable. The openid rebind test additionally runs the real mappers against
 * H2, because it has to prove what the unique openid index and the {@code anon:} rewrite do.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String RAW_PASSWORD = "secret123";

    private static final String REBIND_DB_URL =
            "jdbc:h2:mem:lp_ums_rebind;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    private static KeyPair keyPair;

    @Mock private AuthUserMapper authUserMapper;

    @Mock private WechatMiniappClient wechatMiniappClient;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final InMemoryRefreshTokenStore refreshTokenStore = new InMemoryRefreshTokenStore();

    private MiniappProperties miniappProperties;

    private AuthServiceImpl authService;

    /**
     * Generates a throwaway RSA key pair for the real issuer.
     *
     * @throws Exception when the RSA provider is unavailable
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    /**
     * Builds the service under test with a mocked mapper and a real issuer.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        miniappProperties = new MiniappProperties();
        miniappProperties.setEnabled(true);
        authService =
                new AuthServiceImpl(
                        authUserMapper,
                        tokenService(),
                        refreshTokenStore,
                        passwordEncoder,
                        miniappProperties,
                        wechatMiniappClient);
    }

    /**
     * Builds the real token issuer with the throwaway key pair.
     *
     * @return issuer signing with the test key
     * @author Caspian
     * @since 0.3.0
     */
    private static JwtTokenService tokenService() {
        return new JwtTokenService(
                (RSAPrivateKey) keyPair.getPrivate(),
                "auth-service",
                Duration.ofMinutes(15),
                Duration.ofDays(7));
    }

    /**
     * Returns tokens and the user summary for a valid active account.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void logsInWithValidCredentials() {
        UserAccount account = account(7L, 1, passwordEncoder.encode(RAW_PASSWORD));
        when(authUserMapper.selectByUsername("alice")).thenReturn(account);
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));

        LoginResponse response = authService.login(request("alice", RAW_PASSWORD), "web");

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getExpiresIn()).isEqualTo(900);
        assertThat(response.getUser().getId()).isEqualTo(7L);
        assertThat(response.getUser().getNickname()).isEqualTo("Alice");
        assertThat(response.getUser().getRoles()).containsExactly("STUDENT");
        verify(authUserMapper).touchLastLogin(7L);

        RefreshSession session = refreshTokenStore.get(response.getRefreshToken());
        assertThat(session).isNotNull();
        assertThat(session.getUserId()).isEqualTo(7L);
        assertThat(session.getSource()).isEqualTo("web");
    }

    /**
     * Rejects a wrong password with {@code 40101} and does not record a login.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsWrongPassword() {
        when(authUserMapper.selectByUsername("alice"))
                .thenReturn(account(7L, 1, passwordEncoder.encode(RAW_PASSWORD)));

        assertThatThrownBy(() -> authService.login(request("alice", "wrong-pass"), "web"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
        verify(authUserMapper, never()).touchLastLogin(anyLong());
    }

    /**
     * Rejects an unknown account with the same {@code 40101} code as a wrong password.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsUnknownAccountWithoutLeakingExistence() {
        when(authUserMapper.selectByUsername("nobody")).thenReturn(null);

        assertThatThrownBy(() -> authService.login(request("nobody", RAW_PASSWORD), "web"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
        verify(authUserMapper, never()).touchLastLogin(anyLong());
    }

    /**
     * Rejects a disabled account with {@code 40301} after a correct password.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsDisabledAccount() {
        when(authUserMapper.selectByUsername("alice"))
                .thenReturn(account(7L, 0, passwordEncoder.encode(RAW_PASSWORD)));

        assertThatThrownBy(() -> authService.login(request("alice", RAW_PASSWORD), "web"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        verify(authUserMapper, never()).touchLastLogin(anyLong());
    }

    /**
     * Lets an account inside the cancellation cooling-off period sign in.
     *
     * <p>The user must still be able to review and withdraw the request, so {@code status=2} keeps
     * the login path open; only new orders and payments are refused later in the order flow.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void logsInDuringCoolingOffPeriod() {
        when(authUserMapper.selectByUsername("alice"))
                .thenReturn(
                        account(7L, UserStatus.COOLING_OFF, passwordEncoder.encode(RAW_PASSWORD)));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(authUserMapper.selectPermissionCodes(7L)).thenReturn(List.of());

        LoginResponse response = authService.login(request("alice", RAW_PASSWORD), "web");

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getUser().getId()).isEqualTo(7L);
    }

    /**
     * Reports a cancelled account as {@code 67001} instead of the ban code {@code 40301}.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void rejectsCancelledAccountWithDedicatedCode() {
        when(authUserMapper.selectByUsername("alice"))
                .thenReturn(
                        account(7L, UserStatus.CANCELLED, passwordEncoder.encode(RAW_PASSWORD)));

        assertThatThrownBy(() -> authService.login(request("alice", RAW_PASSWORD), "web"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.ACCOUNT_CANCELLED.getCode()));
        verify(authUserMapper, never()).touchLastLogin(anyLong());
    }

    /**
     * Rejects a cancelled account with {@code 67001} even when the password does not match.
     *
     * <p>Anonymisation stores a random password, so a returning user cannot produce a matching one;
     * reporting {@code 40101} would tell them the password is wrong instead of that the account was
     * cancelled. This proves the status check runs before the password check.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void rejectsCancelledAccountBeforeCheckingThePassword() {
        when(authUserMapper.selectByUsername("alice"))
                .thenReturn(
                        account(7L, UserStatus.CANCELLED, passwordEncoder.encode(RAW_PASSWORD)));

        assertThatThrownBy(() -> authService.login(request("alice", "wrong-pass"), "web"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.ACCOUNT_CANCELLED.getCode()));
        verify(authUserMapper, never()).touchLastLogin(anyLong());
    }

    /**
     * Keeps the password check first for disabled accounts, which still answer {@code 40101}.
     *
     * <p>Only {@code status=3} moved ahead of the password; a disabled account must not reveal its
     * status to somebody who cannot present the password.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void keepsPasswordCheckFirstForDisabledAccount() {
        when(authUserMapper.selectByUsername("alice"))
                .thenReturn(account(7L, 0, passwordEncoder.encode(RAW_PASSWORD)));

        assertThatThrownBy(() -> authService.login(request("alice", "wrong-pass"), "web"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
    }

    /**
     * Creates a student account, hashes the password and stores the refresh session at once.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void registersStudentAndStoresRefreshSession() {
        when(authUserMapper.selectByUsername("newbie")).thenReturn(null);
        when(authUserMapper.insertUser(any(UserAccount.class))).thenAnswer(assignId(9L));
        when(authUserMapper.selectRoleIdByCode(AuthServiceImpl.DEFAULT_ROLE_CODE)).thenReturn(5L);
        when(authUserMapper.selectRoleCodes(9L)).thenReturn(List.of("STUDENT"));
        when(authUserMapper.selectPermissionCodes(9L)).thenReturn(List.of());

        LoginResponse response = authService.register(registerRequest(), "web");

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUser().getId()).isEqualTo(9L);
        assertThat(response.getUser().getNickname()).isEqualTo("Newbie");
        assertThat(response.getUser().getRoles()).containsExactly("STUDENT");

        ArgumentCaptor<UserAccount> inserted = ArgumentCaptor.forClass(UserAccount.class);
        verify(authUserMapper).insertUser(inserted.capture());
        assertThat(inserted.getValue().getPasswordHash())
                .isNotEqualTo(RAW_PASSWORD)
                .startsWith("$2");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, inserted.getValue().getPasswordHash()))
                .isTrue();
        assertThat(inserted.getValue().getEmail()).isNull();
        assertThat(inserted.getValue().getPhone()).isNull();
        verify(authUserMapper).insertUserRole(9L, 5L);

        RefreshSession session = refreshTokenStore.get(response.getRefreshToken());
        assertThat(session).isNotNull();
        assertThat(session.getUserId()).isEqualTo(9L);
        assertThat(session.getSource()).isEqualTo("web");
    }

    /**
     * Rejects a taken login name with {@code 40901} before writing anything.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void registerRejectsDuplicateUsername() {
        when(authUserMapper.selectByUsername("newbie"))
                .thenReturn(account(7L, UserStatus.ACTIVE, "hash"));

        assertThatThrownBy(() -> authService.register(registerRequest(), "web"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.STATE_CONFLICT.getCode()));
        verify(authUserMapper, never()).insertUser(any(UserAccount.class));
        verify(authUserMapper, never()).insertUserRole(anyLong(), anyLong());
    }

    /**
     * Translates a concurrent duplicate key into the same {@code 40901} code.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void registerMapsConcurrentDuplicateToConflict() {
        when(authUserMapper.selectByUsername("newbie")).thenReturn(null);
        when(authUserMapper.insertUser(any(UserAccount.class)))
                .thenThrow(new DuplicateKeyException("uk_username"));

        assertThatThrownBy(() -> authService.register(registerRequest(), "web"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.STATE_CONFLICT.getCode()));
        verify(authUserMapper, never()).insertUserRole(anyLong(), anyLong());
    }

    /**
     * Fails with {@code 50001} when the seeded default role is missing.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void registerRejectsMissingDefaultRole() {
        when(authUserMapper.selectByUsername("newbie")).thenReturn(null);
        when(authUserMapper.insertUser(any(UserAccount.class))).thenAnswer(assignId(9L));
        when(authUserMapper.selectRoleIdByCode(AuthServiceImpl.DEFAULT_ROLE_CODE)).thenReturn(null);

        assertThatThrownBy(() -> authService.register(registerRequest(), "web"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.INTERNAL_ERROR.getCode()));
        verify(authUserMapper, never()).insertUserRole(anyLong(), anyLong());
    }

    /**
     * Rotates a valid refresh token into a new pair and invalidates the presented token.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void refreshRotatesThePresentedToken() {
        UserAccount account = account(7L, 1, passwordEncoder.encode(RAW_PASSWORD));
        when(authUserMapper.selectByUsername("alice")).thenReturn(account);
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(authUserMapper.selectById(7L)).thenReturn(account);

        LoginResponse login = authService.login(request("alice", RAW_PASSWORD), "web");
        String oldRefreshToken = login.getRefreshToken();

        RefreshResponse refreshed = authService.refresh(refreshRequest(oldRefreshToken));

        assertThat(refreshed.getAccessToken()).isNotBlank();
        assertThat(refreshed.getRefreshToken()).isNotEqualTo(oldRefreshToken);
        assertThat(refreshed.getExpiresIn()).isEqualTo(900);
        assertThat(refreshTokenStore.contains(oldRefreshToken)).isFalse();
        assertThat(refreshTokenStore.get(refreshed.getRefreshToken())).isNotNull();
        assertThat(refreshTokenStore.get(refreshed.getRefreshToken()).getSource()).isEqualTo("web");
    }

    /**
     * Rejects a rotated token on a second attempt with {@code 40102}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectReusedRefreshToken() {
        refreshTokenStore.store("refresh-1", new RefreshSession(7L, Instant.now(), "web"));
        when(authUserMapper.selectById(7L)).thenReturn(account(7L, 1, "hash"));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));

        authService.refresh(refreshRequest("refresh-1"));

        assertThatThrownBy(() -> authService.refresh(refreshRequest("refresh-1")))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.TOKEN_EXPIRED.getCode()));
    }

    /**
     * Rejects an unknown refresh token with {@code 40102}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectUnknownRefreshToken() {
        assertThatThrownBy(() -> authService.refresh(refreshRequest("never-issued")))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.TOKEN_EXPIRED.getCode()));
    }

    /**
     * Rejects a refresh attempt for a disabled account with {@code 40301}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectRefreshForDisabledAccount() {
        refreshTokenStore.store("refresh-1", new RefreshSession(7L, Instant.now(), "web"));
        when(authUserMapper.selectById(7L)).thenReturn(account(7L, 0, "hash"));

        assertThatThrownBy(() -> authService.refresh(refreshRequest("refresh-1")))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        assertThat(refreshTokenStore.contains("refresh-1")).isFalse();
    }

    /**
     * Rotates a refresh token for an account inside the cooling-off period.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void refreshAllowsCoolingOffAccount() {
        refreshTokenStore.store("refresh-1", new RefreshSession(7L, Instant.now(), "web"));
        when(authUserMapper.selectById(7L)).thenReturn(account(7L, UserStatus.COOLING_OFF, "hash"));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(authUserMapper.selectPermissionCodes(7L)).thenReturn(List.of());

        RefreshResponse refreshed = authService.refresh(refreshRequest("refresh-1"));

        assertThat(refreshed.getAccessToken()).isNotBlank();
        assertThat(refreshed.getRefreshToken()).isNotEqualTo("refresh-1");
    }

    /**
     * Refuses to rotate a refresh token for a cancelled account with {@code 67001}.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void rejectRefreshForCancelledAccount() {
        refreshTokenStore.store("refresh-1", new RefreshSession(7L, Instant.now(), "web"));
        when(authUserMapper.selectById(7L)).thenReturn(account(7L, UserStatus.CANCELLED, "hash"));

        assertThatThrownBy(() -> authService.refresh(refreshRequest("refresh-1")))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.ACCOUNT_CANCELLED.getCode()));
    }

    /**
     * Rejects a refresh attempt whose account no longer exists with {@code 40101}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectRefreshForMissingAccount() {
        refreshTokenStore.store("refresh-1", new RefreshSession(7L, Instant.now(), "web"));
        when(authUserMapper.selectById(7L)).thenReturn(null);

        assertThatThrownBy(() -> authService.refresh(refreshRequest("refresh-1")))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
    }

    /**
     * Revokes the presented token and treats a repeated logout as success.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void logoutRevokesAndIsIdempotent() {
        refreshTokenStore.store("refresh-1", new RefreshSession(7L, Instant.now(), "web"));

        authService.logout(logoutRequest("refresh-1"));
        assertThat(refreshTokenStore.contains("refresh-1")).isFalse();

        assertThatCode(() -> authService.logout(logoutRequest("refresh-1")))
                .doesNotThrowAnyException();
    }

    /**
     * Returns the profile with effective roles and permissions for an active account.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void meReturnsRolesAndPermissions() {
        UserAccount account = account(7L, 1, "hash");
        account.setEmail("alice@example.com");
        account.setPhone("[PHONE_1]");
        when(authUserMapper.selectById(7L)).thenReturn(account);
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("TEACHER"));
        when(authUserMapper.selectPermissionCodes(7L)).thenReturn(List.of("teacher:course:manage"));

        MeResponse me = authService.me(7L);

        assertThat(me.getId()).isEqualTo(7L);
        assertThat(me.getUsername()).isEqualTo("alice");
        assertThat(me.getEmail()).isEqualTo("alice@example.com");
        assertThat(me.getRoles()).containsExactly("TEACHER");
        assertThat(me.getPermissions()).containsExactly("teacher:course:manage");
    }

    /**
     * Rejects a profile request whose account no longer exists with {@code 40101}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void meRejectsMissingAccount() {
        when(authUserMapper.selectById(7L)).thenReturn(null);

        assertThatThrownBy(() -> authService.me(7L))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
    }

    /**
     * Rejects a profile request for a disabled account with {@code 40301}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void meRejectsDisabledAccount() {
        when(authUserMapper.selectById(7L)).thenReturn(account(7L, 0, "hash"));

        assertThatThrownBy(() -> authService.me(7L))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
    }

    /**
     * Serves the profile for an account inside the cooling-off period.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void meAllowsCoolingOffAccount() {
        when(authUserMapper.selectById(7L)).thenReturn(account(7L, UserStatus.COOLING_OFF, "hash"));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(authUserMapper.selectPermissionCodes(7L)).thenReturn(List.of());

        MeResponse me = authService.me(7L);

        assertThat(me.getId()).isEqualTo(7L);
    }

    /**
     * Rejects the profile of a cancelled account with {@code 67001} immediately.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void meRejectsCancelledAccount() {
        when(authUserMapper.selectById(7L)).thenReturn(account(7L, UserStatus.CANCELLED, "hash"));

        assertThatThrownBy(() -> authService.me(7L))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.ACCOUNT_CANCELLED.getCode()));
    }

    /**
     * Signs an existing openid in without creating another account.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void miniappLoginSignsInExistingOpenid() {
        when(wechatMiniappClient.exchange("code-old"))
                .thenReturn(new WechatSession("openid-old", null, "session-key-old"));
        UserWechat binding = new UserWechat();
        binding.setUserId(7L);
        when(authUserMapper.selectByOpenid("openid-old")).thenReturn(binding);
        when(authUserMapper.selectById(7L)).thenReturn(account(7L, 1, "hash"));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(authUserMapper.selectPermissionCodes(7L)).thenReturn(List.of());

        MiniappLoginResponse response = authService.miniappLogin(miniappRequest("code-old"));

        assertThat(response.isNewUser()).isFalse();
        assertThat(response.getUser().getId()).isEqualTo(7L);
        assertThat(response.getUser().getRoles()).containsExactly("STUDENT");
        assertThat(response.getAccessToken()).isNotBlank();
        verify(authUserMapper, never()).insertUser(any(UserAccount.class));
        verify(authUserMapper, never()).insertWechatBinding(any(UserWechat.class));
        RefreshSession session = refreshTokenStore.get(response.getRefreshToken());
        assertThat(session).isNotNull();
        assertThat(session.getUserId()).isEqualTo(7L);
        assertThat(session.getSource()).isEqualTo(IdentityHeaders.SOURCE_MINIAPP);
    }

    /**
     * Refuses a miniapp sign-in whose bound account was cancelled with {@code 67001}.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void miniappLoginRejectsCancelledAccount() {
        when(wechatMiniappClient.exchange("code-old"))
                .thenReturn(new WechatSession("openid-old", null, "session-key-old"));
        UserWechat binding = new UserWechat();
        binding.setUserId(7L);
        when(authUserMapper.selectByOpenid("openid-old")).thenReturn(binding);
        when(authUserMapper.selectById(7L)).thenReturn(account(7L, UserStatus.CANCELLED, "hash"));

        assertThatThrownBy(() -> authService.miniappLogin(miniappRequest("code-old")))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.ACCOUNT_CANCELLED.getCode()));
        verify(authUserMapper, never()).insertUser(any(UserAccount.class));
    }

    /**
     * Creates a student account, binding and tokens on the first login of an openid.
     *
     * @throws Exception when the response cannot be serialized
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void miniappLoginCreatesStudentOnFirstLogin() throws Exception {
        when(wechatMiniappClient.exchange("code-new"))
                .thenReturn(new WechatSession("openid-new", "union-new", "session-key-new"));
        when(authUserMapper.selectByOpenid("openid-new")).thenReturn(null);
        when(authUserMapper.insertUser(any(UserAccount.class))).thenAnswer(assignId(9L));
        when(authUserMapper.selectRoleIdByCode(AuthServiceImpl.DEFAULT_ROLE_CODE)).thenReturn(5L);
        when(authUserMapper.selectRoleCodes(9L)).thenReturn(List.of("STUDENT"));
        when(authUserMapper.selectPermissionCodes(9L)).thenReturn(List.of());

        MiniappLoginResponse response = authService.miniappLogin(miniappRequest("code-new"));

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.getUser().getRoles()).containsExactly("STUDENT");

        ArgumentCaptor<UserAccount> inserted = ArgumentCaptor.forClass(UserAccount.class);
        verify(authUserMapper).insertUser(inserted.capture());
        assertThat(inserted.getValue().getUsername()).startsWith("mp_").hasSize(19);
        assertThat(inserted.getValue().getPasswordHash()).startsWith("$2");
        assertThat(inserted.getValue().getNickname()).startsWith("微信用户");
        verify(authUserMapper).insertUserRole(9L, 5L);

        ArgumentCaptor<UserWechat> bound = ArgumentCaptor.forClass(UserWechat.class);
        verify(authUserMapper).insertWechatBinding(bound.capture());
        assertThat(bound.getValue().getUserId()).isEqualTo(9L);
        assertThat(bound.getValue().getOpenid()).isEqualTo("openid-new");
        assertThat(bound.getValue().getUnionid()).isEqualTo("union-new");
        assertThat(bound.getValue().getSessionKey()).isEqualTo("session-key-new");

        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).doesNotContain("sessionKey").doesNotContain("session-key-new");
    }

    /**
     * Rejects every call with {@code 66003} while the feature switch is off.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void miniappLoginRejectsWhenDisabled() {
        miniappProperties.setEnabled(false);

        assertThatThrownBy(() -> authService.miniappLogin(miniappRequest("code-new")))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.MINIAPP_LOGIN_DISABLED.getCode()));
        verifyNoInteractions(wechatMiniappClient);
        verify(authUserMapper, never()).selectByOpenid(any());
    }

    /**
     * Passes a WeChat rejection through as {@code 66001} without touching account tables.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void miniappLoginPropagatesWechatFailure() {
        when(wechatMiniappClient.exchange("bad-code"))
                .thenThrow(new BizException(ErrorCode.MINIAPP_LOGIN_BIZ_ERROR));

        assertThatThrownBy(() -> authService.miniappLogin(miniappRequest("bad-code")))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.MINIAPP_LOGIN_BIZ_ERROR.getCode()));
        verify(authUserMapper, never()).selectByOpenid(any());
    }

    /**
     * AC-16: an anonymised openid binds to a brand new account that cannot see the old one.
     *
     * <p>The real mappers run against H2, so the unique openid index, the {@code anon:} rewrite and
     * the generated ids behave as in production; only the trade call is mocked, because trade owns
     * a different database. Trade rows stay attached to the old user id, so a query keyed by the
     * new id never matches them.
     *
     * @throws Exception when the schema or the MyBatis factory cannot be built
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void anonymizedOpenidRegistersANewAccountThatCannotSeeTheOldOne() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(REBIND_DB_URL, "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createMiniappSchema(jdbc);
        SqlSessionFactory factory = mybatisFactory(dataSource);
        AuthUserMapper mapper = new SqlSessionTemplate(factory).getMapper(AuthUserMapper.class);
        AccountAnonymizationService anonymization =
                new AccountAnonymizationService(
                        new SqlSessionTemplate(factory).getMapper(AccountAnonymizationMapper.class),
                        mock(AccountAnonymizationClient.class),
                        deletionProperties(),
                        passwordEncoder);
        AuthServiceImpl realService =
                new AuthServiceImpl(
                        mapper,
                        tokenService(),
                        refreshTokenStore,
                        passwordEncoder,
                        miniappProperties,
                        wechatMiniappClient);
        when(wechatMiniappClient.exchange("code-rebind"))
                .thenReturn(new WechatSession("openid-rebind", null, "session-rebind"));

        long oldUserId = realService.miniappLogin(miniappRequest("code-rebind")).getUser().getId();
        jdbc.update("UPDATE sys_user SET status = 2 WHERE id = ?", oldUserId);
        assertThat(anonymization.anonymize(oldUserId)).isTrue();

        MiniappLoginResponse second = realService.miniappLogin(miniappRequest("code-rebind"));
        long newUserId = second.getUser().getId();

        assertThat(second.isNewUser()).isTrue();
        assertThat(newUserId).isNotEqualTo(oldUserId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM sys_user_wechat "
                                        + "WHERE user_id = ? AND deleted = 0 AND openid = ?",
                                Integer.class,
                                newUserId,
                                "openid-rebind"))
                .isEqualTo(1);
        Map<String, Object> oldBinding =
                jdbc.queryForMap(
                        "SELECT openid, deleted FROM sys_user_wechat WHERE user_id = ?", oldUserId);
        assertThat(oldBinding.get("openid").toString()).startsWith("anon:");
        assertThat(((Number) oldBinding.get("deleted")).intValue()).isEqualTo(1);
        assertThat(mapper.selectByOpenid("openid-rebind").getUserId()).isEqualTo(newUserId);
        assertThat(realService.me(newUserId).getUsername()).startsWith("mp_");
        assertThat(mapper.selectById(newUserId).getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(mapper.selectById(oldUserId).getStatus()).isEqualTo(UserStatus.CANCELLED);
    }

    /**
     * Builds the MyBatis factory with the two mappers under test bound to the H2 data source.
     *
     * @param dataSource H2 data source carrying the {@code lp_ums} tables
     * @return factory whose configuration exposes the production mapper statements
     * @throws Exception when the factory cannot be built
     * @author Caspian
     * @since 0.3.0
     */
    private static SqlSessionFactory mybatisFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        SqlSessionFactory sessionFactory = factory.getObject();
        sessionFactory.getConfiguration().addMapper(AuthUserMapper.class);
        sessionFactory.getConfiguration().addMapper(AccountAnonymizationMapper.class);
        return sessionFactory;
    }

    /**
     * Creates the account and role tables the miniapp login path reads, with the default role row.
     *
     * @param jdbc template bound to the H2 data source
     * @author Caspian
     * @since 0.3.0
     */
    private static void createMiniappSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE ALIAS IF NOT EXISTS SHA2 FOR \"com.lp.auth.support.Sha2Alias.sha2\"");
        jdbc.execute(
                "CREATE TABLE sys_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(32) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL, nickname VARCHAR(32), avatar_url VARCHAR(255), email VARCHAR(64), phone VARCHAR(20), gender TINYINT NOT NULL DEFAULT 0, status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0, last_login_at DATETIME, created_at DATETIME, updated_at DATETIME)");
        jdbc.execute(
                "CREATE TABLE sys_user_wechat (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, openid VARCHAR(128) NOT NULL UNIQUE, unionid VARCHAR(128), session_key VARCHAR(255), deleted TINYINT NOT NULL DEFAULT 0, created_at DATETIME, updated_at DATETIME)");
        jdbc.execute(
                "CREATE TABLE sys_role (id BIGINT PRIMARY KEY, code VARCHAR(32) NOT NULL, status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0)");
        jdbc.execute(
                "CREATE TABLE sys_user_role (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0)");
        jdbc.execute(
                "CREATE TABLE sys_menu (id BIGINT PRIMARY KEY, permission_code VARCHAR(64), status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0)");
        jdbc.execute(
                "CREATE TABLE sys_role_menu (id BIGINT AUTO_INCREMENT PRIMARY KEY, role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0)");
        jdbc.update("INSERT INTO sys_role (id, code) VALUES (5, 'STUDENT')");
    }

    /**
     * Builds deletion settings whose salt is fixed for the deterministic openid digest.
     *
     * @return settings accepted by the anonymisation service
     * @author Caspian
     * @since 0.3.0
     */
    private static AccountDeletionProperties deletionProperties() {
        AccountDeletionProperties properties = new AccountDeletionProperties();
        properties.setAnonymizeSalt("rebind-salt");
        return properties;
    }

    /**
     * Builds a miniapp login body.
     *
     * @param code one-time login code
     * @return request body
     * @author Caspian
     * @since 0.1.0
     */
    private static MiniappLoginRequest miniappRequest(String code) {
        MiniappLoginRequest request = new MiniappLoginRequest();
        request.setCode(code);
        return request;
    }

    /**
     * Builds a valid registration body whose optional fields are blank on purpose.
     *
     * @return request body
     * @author Caspian
     * @since 0.1.0
     */
    private static RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newbie");
        request.setPassword(RAW_PASSWORD);
        request.setNickname("Newbie");
        request.setEmail("  ");
        request.setPhone("");
        return request;
    }

    /**
     * Simulates a generated key being written back into the inserted entity.
     *
     * @param userId id the database would generate
     * @return mock answer returning one inserted row
     * @author Caspian
     * @since 0.1.0
     */
    private static Answer<Integer> assignId(long userId) {
        return invocation -> {
            invocation.<UserAccount>getArgument(0).setId(userId);
            return 1;
        };
    }

    /**
     * Builds a login request.
     *
     * @param account login name
     * @param password raw password
     * @return request body
     * @author Caspian
     * @since 0.1.0
     */
    private static LoginRequest request(String account, String password) {
        LoginRequest request = new LoginRequest();
        request.setAccount(account);
        request.setPassword(password);
        return request;
    }

    /**
     * Builds a refresh request.
     *
     * @param refreshToken opaque refresh token
     * @return request body
     * @author Caspian
     * @since 0.1.0
     */
    private static RefreshRequest refreshRequest(String refreshToken) {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(refreshToken);
        return request;
    }

    /**
     * Builds a logout request.
     *
     * @param refreshToken opaque refresh token
     * @return request body
     * @author Caspian
     * @since 0.1.0
     */
    private static LogoutRequest logoutRequest(String refreshToken) {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken(refreshToken);
        return request;
    }

    /**
     * A ticket owner receives a fresh pair without presenting a password.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void issuesSessionForTicketOwner() {
        when(authUserMapper.selectById(42L)).thenReturn(account(42L, 1, "unused"));

        RefreshResponse response = authService.issueSession(42L, IdentityHeaders.SOURCE_WEB);

        assertThat(response.getAccessToken()).isNotNull();
        assertThat(response.getRefreshToken()).isNotNull();
        assertThat(response.getExpiresIn()).isPositive();
        assertThat(refreshTokenStore.contains(response.getRefreshToken())).isTrue();
    }

    /**
     * A missing account never receives a session.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsSessionForUnknownAccount() {
        when(authUserMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> authService.issueSession(99L, IdentityHeaders.SOURCE_WEB))
                .isInstanceOf(BizException.class)
                .satisfies(
                        exception ->
                                assertThat(((BizException) exception).getCode()).isEqualTo(40101));
    }

    /**
     * A disabled account never receives a session.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsSessionForDisabledAccount() {
        when(authUserMapper.selectById(42L)).thenReturn(account(42L, 0, "unused"));

        assertThatThrownBy(() -> authService.issueSession(42L, IdentityHeaders.SOURCE_WEB))
                .isInstanceOf(BizException.class)
                .satisfies(
                        exception ->
                                assertThat(((BizException) exception).getCode()).isEqualTo(40301));
    }

    /**
     * A cancelled account never receives a session and is reported as {@code 67001}.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void rejectsSessionForCancelledAccount() {
        when(authUserMapper.selectById(42L))
                .thenReturn(account(42L, UserStatus.CANCELLED, "unused"));

        assertThatThrownBy(() -> authService.issueSession(42L, IdentityHeaders.SOURCE_WEB))
                .isInstanceOf(BizException.class)
                .satisfies(
                        exception ->
                                assertThat(((BizException) exception).getCode()).isEqualTo(67001));
    }

    /**
     * Builds an account row.
     *
     * @param id user id
     * @param status account status
     * @param passwordHash BCrypt hash
     * @return account row
     * @author Caspian
     * @since 0.1.0
     */
    private static UserAccount account(long id, int status, String passwordHash) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setUsername("alice");
        account.setNickname("Alice");
        account.setAvatarUrl("https://cdn.example.com/a.png");
        account.setStatus(status);
        account.setPasswordHash(passwordHash);
        return account;
    }

    /**
     * In-memory refresh whitelist used only by unit tests.
     *
     * @author Caspian
     * @since 0.1.0
     */
    private static final class InMemoryRefreshTokenStore implements RefreshTokenStore {

        private final Map<String, RefreshSession> sessions = new HashMap<>();

        /**
         * Stores a session.
         *
         * @param refreshToken raw token used as the map key
         * @param session session metadata
         * @author Caspian
         * @since 0.1.0
         */
        @Override
        public void store(String refreshToken, RefreshSession session) {
            sessions.put(refreshToken, session);
        }

        /**
         * Removes and returns a session, mirroring the atomic Redis read and delete.
         *
         * @param refreshToken raw token
         * @return stored session, or empty when absent
         * @author Caspian
         * @since 0.1.0
         */
        @Override
        public Optional<RefreshSession> consume(String refreshToken) {
            return Optional.ofNullable(sessions.remove(refreshToken));
        }

        /**
         * Removes a session.
         *
         * @param refreshToken raw token
         * @author Caspian
         * @since 0.1.0
         */
        @Override
        public void revoke(String refreshToken) {
            sessions.remove(refreshToken);
        }

        /**
         * Tells whether a token is still stored.
         *
         * @param refreshToken raw token
         * @return {@code true} when present
         * @author Caspian
         * @since 0.1.0
         */
        boolean contains(String refreshToken) {
            return sessions.containsKey(refreshToken);
        }

        /**
         * Returns the stored session.
         *
         * @param refreshToken raw token
         * @return session, or {@code null} when absent
         * @author Caspian
         * @since 0.1.0
         */
        RefreshSession get(String refreshToken) {
            return sessions.get(refreshToken);
        }
    }
}
