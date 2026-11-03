package com.lp.auth.service.impl;

import com.lp.auth.dto.LoginRequest;
import com.lp.auth.dto.LoginResponse;
import com.lp.auth.dto.LoginUser;
import com.lp.auth.dto.LogoutRequest;
import com.lp.auth.dto.MeResponse;
import com.lp.auth.dto.RefreshRequest;
import com.lp.auth.dto.RefreshResponse;
import com.lp.auth.dto.RegisterRequest;
import com.lp.auth.entity.UserAccount;
import com.lp.auth.mapper.AuthUserMapper;
import com.lp.auth.security.AuthTokens;
import com.lp.auth.security.JwtTokenService;
import com.lp.auth.security.RefreshSession;
import com.lp.auth.security.RefreshTokenStore;
import com.lp.auth.service.AuthService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies credentials against {@code lp_ums} and manages refresh sessions.
 *
 * <p>Missing account and wrong password share {@code 40101} so the response never reveals whether
 * an account exists. A non active account returns {@code 40301} after the password check, which
 * keeps disabled accounts from probing passwords. Refresh rotation consumes the presented token
 * atomically, so a token can never be replayed; a disabled account found during refresh keeps the
 * same {@code 40301} contract.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** {@code sys_user.status} value of a usable account. */
    public static final int STATUS_ACTIVE = 1;

    /** Role granted to every self registered account. */
    public static final String DEFAULT_ROLE_CODE = "STUDENT";

    private final AuthUserMapper authUserMapper;

    private final JwtTokenService jwtTokenService;

    private final RefreshTokenStore refreshTokenStore;

    private final PasswordEncoder passwordEncoder;

    /**
     * Creates the service with its collaborators.
     *
     * @param authUserMapper account and role queries
     * @param jwtTokenService token issuer
     * @param refreshTokenStore refresh session whitelist
     * @param passwordEncoder BCrypt verifier
     * @author Caspian
     * @since 0.1.0
     */
    public AuthServiceImpl(
            AuthUserMapper authUserMapper,
            JwtTokenService jwtTokenService,
            RefreshTokenStore refreshTokenStore,
            PasswordEncoder passwordEncoder) {
        this.authUserMapper = authUserMapper;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verifies the account password and issues a token pair.
     *
     * @param request validated login credentials
     * @param source source platform recorded with the refresh session
     * @return tokens plus the authenticated user summary
     * @throws BizException {@code 40101} when the account is missing or the password is wrong,
     *     {@code 40301} when the account is not active, {@code 50001} when the refresh store is
     *     unreachable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public LoginResponse login(LoginRequest request, String source) {
        UserAccount account = authUserMapper.selectByUsername(request.getAccount());
        if (account == null
                || !passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (account.getStatus() == null || account.getStatus() != STATUS_ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "account disabled");
        }

        List<String> roles = authUserMapper.selectRoleCodes(account.getId());
        List<String> permissions = authUserMapper.selectPermissionCodes(account.getId());
        authUserMapper.touchLastLogin(account.getId());
        AuthTokens tokens = jwtTokenService.issue(account.getId(), roles, permissions);
        refreshTokenStore.store(
                tokens.getRefreshToken(),
                new RefreshSession(account.getId(), Instant.now(), source));
        LoginUser user =
                new LoginUser(
                        account.getId(), account.getNickname(), account.getAvatarUrl(), roles);
        return new LoginResponse(
                tokens.getAccessToken(),
                tokens.getRefreshToken(),
                tokens.getExpiresInSeconds(),
                user);
    }

    /**
     * Creates a student account and issues a token pair in the same call.
     *
     * <p>The login name is checked first so the common case returns {@code 40901} with a clear
     * message; the unique key remains the authority and a concurrent insert is translated into the
     * same code. The account insert and the default role grant share one transaction, so a
     * partially registered account can never exist.
     *
     * @param request validated registration body
     * @param source source platform recorded with the refresh session
     * @return tokens plus the new user summary carrying {@code STUDENT}
     * @throws BizException {@code 40901} when the login name, email or phone already exists, {@code
     *     50001} when the default role is missing or the refresh store is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request, String source) {
        String username = request.getUsername().trim();
        if (authUserMapper.selectByUsername(username) != null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "username already exists");
        }

        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setNickname(request.getNickname().trim());
        account.setEmail(blankToNull(request.getEmail()));
        account.setPhone(blankToNull(request.getPhone()));
        try {
            authUserMapper.insertUser(account);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "username already exists");
        }

        long userId = account.getId();
        Long roleId = authUserMapper.selectRoleIdByCode(DEFAULT_ROLE_CODE);
        if (roleId == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "default role is not configured");
        }
        authUserMapper.insertUserRole(userId, roleId);

        List<String> roles = authUserMapper.selectRoleCodes(userId);
        List<String> permissions = authUserMapper.selectPermissionCodes(userId);
        AuthTokens tokens = jwtTokenService.issue(userId, roles, permissions);
        refreshTokenStore.store(
                tokens.getRefreshToken(), new RefreshSession(userId, Instant.now(), source));
        LoginUser user =
                new LoginUser(userId, account.getNickname(), account.getAvatarUrl(), roles);
        return new LoginResponse(
                tokens.getAccessToken(),
                tokens.getRefreshToken(),
                tokens.getExpiresInSeconds(),
                user);
    }

    /**
     * Rotates the presented refresh token into a new token pair.
     *
     * @param request the refresh token to consume
     * @return a new access token and a new refresh token
     * @throws BizException {@code 40102} when the token is unknown, expired or already used, {@code
     *     40101} when the owning account no longer exists, {@code 40301} when the owning account is
     *     disabled, {@code 50001} when the refresh store is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public RefreshResponse refresh(RefreshRequest request) {
        RefreshSession session =
                refreshTokenStore
                        .consume(request.getRefreshToken())
                        .orElseThrow(() -> new BizException(ErrorCode.TOKEN_EXPIRED));

        UserAccount account = authUserMapper.selectById(session.getUserId());
        if (account == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (account.getStatus() == null || account.getStatus() != STATUS_ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "account disabled");
        }

        List<String> roles = authUserMapper.selectRoleCodes(account.getId());
        List<String> permissions = authUserMapper.selectPermissionCodes(account.getId());
        AuthTokens tokens = jwtTokenService.issue(account.getId(), roles, permissions);
        refreshTokenStore.store(
                tokens.getRefreshToken(),
                new RefreshSession(account.getId(), Instant.now(), session.getSource()));
        return new RefreshResponse(
                tokens.getAccessToken(), tokens.getRefreshToken(), tokens.getExpiresInSeconds());
    }

    /**
     * Revokes the presented refresh token.
     *
     * @param request the refresh token to revoke
     * @throws BizException {@code 50001} when the refresh store is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public void logout(LogoutRequest request) {
        refreshTokenStore.revoke(request.getRefreshToken());
    }

    /**
     * Returns the current profile with effective roles and permissions.
     *
     * @param userId authentic user id resolved from the trusted gateway header
     * @return profile, roles and permission codes
     * @throws BizException {@code 40101} when the account no longer exists, {@code 40301} when the
     *     account is not active
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public MeResponse me(long userId) {
        UserAccount account = authUserMapper.selectById(userId);
        if (account == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (account.getStatus() == null || account.getStatus() != STATUS_ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "account disabled");
        }
        return new MeResponse(
                account.getId(),
                account.getUsername(),
                account.getNickname(),
                account.getAvatarUrl(),
                account.getEmail(),
                account.getPhone(),
                authUserMapper.selectRoleCodes(userId),
                authUserMapper.selectPermissionCodes(userId));
    }

    /**
     * Normalizes a blank optional value to {@code null} so it cannot occupy a unique key.
     *
     * @param value raw optional value
     * @return trimmed value, or {@code null} when absent or blank
     * @author Caspian
     * @since 0.1.0
     */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
