package com.lp.auth.service.impl;

import com.lp.auth.dto.LoginRequest;
import com.lp.auth.dto.LoginResponse;
import com.lp.auth.dto.LoginUser;
import com.lp.auth.dto.LogoutRequest;
import com.lp.auth.dto.RefreshRequest;
import com.lp.auth.dto.RefreshResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Verifies credentials against {@code lp_ums} and manages refresh sessions.
 *
 * <p>Missing account and wrong password share {@code 40101} so the response never reveals
 * whether an account exists. A non active account returns {@code 40301} after the password
 * check, which keeps disabled accounts from probing passwords. Refresh rotation consumes the
 * presented token atomically, so a token can never be replayed; a disabled account found during
 * refresh keeps the same {@code 40301} contract.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** {@code sys_user.status} value of a usable account. */
    public static final int STATUS_ACTIVE = 1;

    private final AuthUserMapper authUserMapper;

    private final JwtTokenService jwtTokenService;

    private final RefreshTokenStore refreshTokenStore;

    private final PasswordEncoder passwordEncoder;

    /**
     * Creates the service with its collaborators.
     *
     * @param authUserMapper    account and role queries
     * @param jwtTokenService   token issuer
     * @param refreshTokenStore refresh session whitelist
     * @param passwordEncoder   BCrypt verifier
     * @author Caspian
     * @since 0.1.0
     */
    public AuthServiceImpl(AuthUserMapper authUserMapper, JwtTokenService jwtTokenService,
                           RefreshTokenStore refreshTokenStore, PasswordEncoder passwordEncoder) {
        this.authUserMapper = authUserMapper;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verifies the account password and issues a token pair.
     *
     * @param request validated login credentials
     * @param source  source platform recorded with the refresh session
     * @return tokens plus the authenticated user summary
     * @throws BizException {@code 40101} when the account is missing or the password is wrong,
     *                      {@code 40301} when the account is not active,
     *                      {@code 50001} when the refresh store is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public LoginResponse login(LoginRequest request, String source) {
        UserAccount account = authUserMapper.selectByUsername(request.getAccount());
        if (account == null || !passwordEncoder.matches(request.getPassword(),
                account.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (account.getStatus() == null || account.getStatus() != STATUS_ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "account disabled");
        }

        List<String> roles = authUserMapper.selectRoleCodes(account.getId());
        authUserMapper.touchLastLogin(account.getId());
        AuthTokens tokens = jwtTokenService.issue(account.getId(), roles);
        refreshTokenStore.store(tokens.getRefreshToken(),
                new RefreshSession(account.getId(), Instant.now(), source));
        LoginUser user = new LoginUser(account.getId(), account.getNickname(),
                account.getAvatarUrl(), roles);
        return new LoginResponse(tokens.getAccessToken(), tokens.getRefreshToken(),
                tokens.getExpiresInSeconds(), user);
    }

    /**
     * Rotates the presented refresh token into a new token pair.
     *
     * @param request the refresh token to consume
     * @return a new access token and a new refresh token
     * @throws BizException {@code 40102} when the token is unknown, expired or already used,
     *                      {@code 40101} when the owning account no longer exists,
     *                      {@code 40301} when the owning account is disabled,
     *                      {@code 50001} when the refresh store is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public RefreshResponse refresh(RefreshRequest request) {
        RefreshSession session = refreshTokenStore.consume(request.getRefreshToken())
                .orElseThrow(() -> new BizException(ErrorCode.TOKEN_EXPIRED));

        UserAccount account = authUserMapper.selectById(session.getUserId());
        if (account == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (account.getStatus() == null || account.getStatus() != STATUS_ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "account disabled");
        }

        List<String> roles = authUserMapper.selectRoleCodes(account.getId());
        AuthTokens tokens = jwtTokenService.issue(account.getId(), roles);
        refreshTokenStore.store(tokens.getRefreshToken(),
                new RefreshSession(account.getId(), Instant.now(), session.getSource()));
        return new RefreshResponse(tokens.getAccessToken(), tokens.getRefreshToken(),
                tokens.getExpiresInSeconds());
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
}
