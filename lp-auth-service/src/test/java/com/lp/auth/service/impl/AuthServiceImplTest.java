package com.lp.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.auth.config.MiniappProperties;
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
import com.lp.auth.mapper.AuthUserMapper;
import com.lp.auth.security.JwtTokenService;
import com.lp.auth.security.RefreshSession;
import com.lp.auth.security.RefreshTokenStore;
import com.lp.auth.security.WechatMiniappClient;
import com.lp.auth.security.WechatSession;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Covers the login, refresh and logout decision branches without a database or Redis.
 *
 * <p>The token issuer is real and signs with a generated RSA key, while persistence and the refresh
 * whitelist are in-memory stand-ins, so the asserted tokens are genuinely verifiable and the
 * rotation rules are observable.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String RAW_PASSWORD = "secret123";

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
        JwtTokenService tokenService =
                new JwtTokenService(
                        (RSAPrivateKey) keyPair.getPrivate(),
                        "auth-service",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7));
        miniappProperties = new MiniappProperties();
        miniappProperties.setEnabled(true);
        authService =
                new AuthServiceImpl(
                        authUserMapper,
                        tokenService,
                        refreshTokenStore,
                        passwordEncoder,
                        miniappProperties,
                        wechatMiniappClient);
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
                .thenReturn(account(7L, AuthServiceImpl.STATUS_ACTIVE, "hash"));

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
