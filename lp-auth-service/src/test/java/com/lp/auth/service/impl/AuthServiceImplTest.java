package com.lp.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.auth.dto.LoginRequest;
import com.lp.auth.dto.LoginResponse;
import com.lp.auth.entity.UserAccount;
import com.lp.auth.mapper.AuthUserMapper;
import com.lp.auth.security.JwtTokenService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Covers the login decision branches without a database.
 *
 * <p>The token issuer is real and signs with a generated RSA key; only the persistence
 * mapper is mocked, so the asserted tokens are genuinely verifiable.</p>
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
        authService = new AuthServiceImpl(authUserMapper, tokenService, passwordEncoder);
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

        LoginResponse response = authService.login(request("alice", RAW_PASSWORD));

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getExpiresIn()).isEqualTo(900);
        assertThat(response.getUser().getId()).isEqualTo(7L);
        assertThat(response.getUser().getNickname()).isEqualTo("Alice");
        assertThat(response.getUser().getRoles()).containsExactly("STUDENT");
        verify(authUserMapper).touchLastLogin(7L);
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

        assertThatThrownBy(() -> authService.login(request("alice", "wrong-pass")))
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

        assertThatThrownBy(() -> authService.login(request("nobody", RAW_PASSWORD)))
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

        assertThatThrownBy(() -> authService.login(request("alice", RAW_PASSWORD)))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        verify(authUserMapper, never()).touchLastLogin(anyLong());
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
}
