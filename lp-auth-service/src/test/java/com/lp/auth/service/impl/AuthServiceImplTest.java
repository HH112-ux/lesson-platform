package com.lp.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.auth.dto.LoginRequest;
import com.lp.auth.dto.LoginResponse;
import com.lp.auth.dto.LogoutRequest;
import com.lp.auth.dto.RefreshRequest;
import com.lp.auth.dto.RefreshResponse;
import com.lp.auth.entity.UserAccount;
import com.lp.auth.mapper.AuthUserMapper;
import com.lp.auth.security.JwtTokenService;
import com.lp.auth.security.RefreshSession;
import com.lp.auth.security.RefreshTokenStore;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Covers the login, refresh and logout decision branches without a database or Redis.
 *
 * <p>The token issuer is real and signs with a generated RSA key, while persistence and the
 * refresh whitelist are in-memory stand-ins, so the asserted tokens are genuinely verifiable
 * and the rotation rules are observable.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String RAW_PASSWORD = "secret123";

    private static KeyPair keyPair;

    @Mock
    private AuthUserMapper authUserMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final InMemoryRefreshTokenStore refreshTokenStore = new InMemoryRefreshTokenStore();

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
        JwtTokenService tokenService = new JwtTokenService((RSAPrivateKey) keyPair.getPrivate(),
                "auth-service", Duration.ofMinutes(15), Duration.ofDays(7));
        authService = new AuthServiceImpl(authUserMapper, tokenService, refreshTokenStore,
                passwordEncoder);
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
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
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
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
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
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        verify(authUserMapper, never()).touchLastLogin(anyLong());
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
        assertThat(refreshTokenStore.get(refreshed.getRefreshToken()).getSource())
                .isEqualTo("web");
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
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED.getCode()));
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
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED.getCode()));
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
                .isInstanceOfSatisfying(BizException.class,
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
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
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
     * Builds a login request.
     *
     * @param account  login name
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
     * Builds an account row.
     *
     * @param id           user id
     * @param status       account status
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
         * @param session      session metadata
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
