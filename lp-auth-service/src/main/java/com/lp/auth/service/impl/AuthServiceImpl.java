package com.lp.auth.service.impl;

import com.lp.auth.config.MiniappProperties;
import com.lp.auth.constant.UserStatus;
import com.lp.auth.dto.LoginRequest;
import com.lp.auth.dto.LoginResponse;
import com.lp.auth.dto.LoginUser;
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
import com.lp.auth.security.AuthTokens;
import com.lp.auth.security.JwtTokenService;
import com.lp.auth.security.RefreshSession;
import com.lp.auth.security.RefreshTokenStore;
import com.lp.auth.security.WechatMiniappClient;
import com.lp.auth.security.WechatSession;
import com.lp.auth.service.AuthService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
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

    /** Role granted to every self registered account. */
    public static final String DEFAULT_ROLE_CODE = "STUDENT";

    private final AuthUserMapper authUserMapper;

    private final JwtTokenService jwtTokenService;

    private final RefreshTokenStore refreshTokenStore;

    private final PasswordEncoder passwordEncoder;

    private final MiniappProperties miniappProperties;

    private final WechatMiniappClient wechatMiniappClient;

    /**
     * Creates the service with its collaborators.
     *
     * @param authUserMapper account and role queries
     * @param jwtTokenService token issuer
     * @param refreshTokenStore refresh session whitelist
     * @param passwordEncoder BCrypt verifier
     * @param miniappProperties miniapp feature switch and credentials
     * @param wechatMiniappClient miniapp code exchange client
     * @author Caspian
     * @since 0.1.0
     */
    public AuthServiceImpl(
            AuthUserMapper authUserMapper,
            JwtTokenService jwtTokenService,
            RefreshTokenStore refreshTokenStore,
            PasswordEncoder passwordEncoder,
            MiniappProperties miniappProperties,
            WechatMiniappClient wechatMiniappClient) {
        this.authUserMapper = authUserMapper;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.passwordEncoder = passwordEncoder;
        this.miniappProperties = miniappProperties;
        this.wechatMiniappClient = wechatMiniappClient;
    }

    /**
     * Verifies the account password and issues a token pair.
     *
     * @param request validated login credentials
     * @param source source platform recorded with the refresh session
     * @return tokens plus the authenticated user summary
     * @throws BizException {@code 40101} when the account is missing or the password is wrong,
     *     {@code 67001} when the account was cancelled, {@code 40301} when the account is disabled,
     *     {@code 50001} when the refresh store is unreachable
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
        requireLoginAllowed(account);

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
     * Exchanges a miniapp code for a token pair, creating a student account on first login.
     *
     * <p>The switch is checked before any upstream call, so a deployment without an AppSecret
     * answers {@code 66003} instead of failing inside WeChat. A known openid signs in the existing
     * account; an unknown one creates an account whose login name is derived from the openid hash,
     * grants {@code STUDENT} and stores the binding in the same transaction. The refresh session
     * always records {@code miniapp} as its source, and the WeChat session key stays server side.
     *
     * @param request validated miniapp login body
     * @return tokens, the new-user flag and the user summary
     * @throws BizException {@code 66003} when the feature is disabled, {@code 66001} when WeChat
     *     rejects the code, {@code 40101} when a bound account disappeared, {@code 67001} when the
     *     bound account was cancelled, {@code 40301} when the bound account is disabled, {@code
     *     50001} when the default role or the refresh store is unavailable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional
    public MiniappLoginResponse miniappLogin(MiniappLoginRequest request) {
        if (!miniappProperties.isEnabled()) {
            throw new BizException(ErrorCode.MINIAPP_LOGIN_DISABLED);
        }

        WechatSession session = wechatMiniappClient.exchange(request.getCode());
        UserWechat binding = authUserMapper.selectByOpenid(session.getOpenid());
        if (binding != null) {
            UserAccount account = authUserMapper.selectById(binding.getUserId());
            if (account == null) {
                throw new BizException(ErrorCode.UNAUTHENTICATED);
            }
            requireLoginAllowed(account);
            return issueMiniappTokens(account, false);
        }

        UserAccount account = createMiniappAccount(session);
        UserWechat newBinding = new UserWechat();
        newBinding.setUserId(account.getId());
        newBinding.setOpenid(session.getOpenid());
        newBinding.setUnionid(session.getUnionid());
        newBinding.setSessionKey(session.getSessionKey());
        authUserMapper.insertWechatBinding(newBinding);
        return issueMiniappTokens(account, true);
    }

    /**
     * Rotates the presented refresh token into a new token pair.
     *
     * @param request the refresh token to consume
     * @return a new access token and a new refresh token
     * @throws BizException {@code 40102} when the token is unknown, expired or already used, {@code
     *     40101} when the owning account no longer exists, {@code 67001} when the owning account
     *     was cancelled, {@code 40301} when the owning account is disabled, {@code 50001} when the
     *     refresh store is unreachable
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
        requireLoginAllowed(account);

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
     * @throws BizException {@code 40101} when the account no longer exists, {@code 67001} when the
     *     account was cancelled, {@code 40301} when the account is disabled
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public MeResponse me(long userId) {
        UserAccount account = authUserMapper.selectById(userId);
        if (account == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        requireLoginAllowed(account);
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
     * Creates an account for a first-time openid and grants the default role.
     *
     * <p>The account has no usable password: it receives a BCrypt hash of a random value that is
     * discarded, so password login is impossible while the column stays non-null.
     *
     * @param session resolved WeChat identity
     * @return inserted account with its generated id and nickname
     * @throws BizException {@code 50001} when the default role is missing
     * @author Caspian
     * @since 0.1.0
     */
    private UserAccount createMiniappAccount(WechatSession session) {
        UserAccount account = new UserAccount();
        account.setUsername(miniappUsername(session.getOpenid()));
        account.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        account.setNickname(miniappNickname(session.getOpenid()));
        authUserMapper.insertUser(account);

        long userId = account.getId();
        Long roleId = authUserMapper.selectRoleIdByCode(DEFAULT_ROLE_CODE);
        if (roleId == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "default role is not configured");
        }
        authUserMapper.insertUserRole(userId, roleId);
        return account;
    }

    /**
     * Issues a token pair for a miniapp account and records the refresh session.
     *
     * @param account bound account
     * @param newUser whether this call created the account
     * @return response carrying tokens, the new-user flag and the user summary
     * @throws BizException {@code 50001} when the refresh store is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    private MiniappLoginResponse issueMiniappTokens(UserAccount account, boolean newUser) {
        List<String> roles = authUserMapper.selectRoleCodes(account.getId());
        List<String> permissions = authUserMapper.selectPermissionCodes(account.getId());
        authUserMapper.touchLastLogin(account.getId());
        AuthTokens tokens = jwtTokenService.issue(account.getId(), roles, permissions);
        refreshTokenStore.store(
                tokens.getRefreshToken(),
                new RefreshSession(account.getId(), Instant.now(), IdentityHeaders.SOURCE_MINIAPP));
        LoginUser user =
                new LoginUser(
                        account.getId(), account.getNickname(), account.getAvatarUrl(), roles);
        return new MiniappLoginResponse(
                tokens.getAccessToken(),
                tokens.getRefreshToken(),
                tokens.getExpiresInSeconds(),
                newUser,
                user);
    }

    /**
     * Derives a stable login name from the openid without exposing it.
     *
     * @param openid WeChat openid
     * @return {@code mp_} plus 16 hex characters of the SHA-256 digest
     * @author Caspian
     * @since 0.1.0
     */
    private static String miniappUsername(String openid) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(openid.getBytes(StandardCharsets.UTF_8));
            return "mp_" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    /**
     * Builds a friendly default nickname from the openid suffix.
     *
     * @param openid WeChat openid
     * @return nickname no longer than 32 characters
     * @author Caspian
     * @since 0.1.0
     */
    private static String miniappNickname(String openid) {
        String suffix = openid.length() <= 4 ? openid : openid.substring(openid.length() - 4);
        return "微信用户" + suffix;
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

    /**
     * Applies the shared account status gate used by every sign-in and session entry point.
     *
     * <p>An account inside the cooling-off period stays usable because the user must be able to
     * review and withdraw the request; only new orders and payments are refused later in the order
     * flow. A cancelled account is reported as {@code 67001} so it cannot be confused with a ban,
     * which keeps {@code 40301}.
     *
     * @param account account row loaded from {@code lp_ums}
     * @throws BizException {@code 67001} when the account was cancelled, {@code 40301} when it is
     *     disabled, banned or carries an unknown status
     * @author Caspian
     * @since 0.2.0
     */
    private static void requireLoginAllowed(UserAccount account) {
        Integer status = account.getStatus();
        if (status != null && (status == UserStatus.ACTIVE || status == UserStatus.COOLING_OFF)) {
            return;
        }
        if (status != null && status == UserStatus.CANCELLED) {
            throw new BizException(ErrorCode.ACCOUNT_CANCELLED);
        }
        throw new BizException(ErrorCode.FORBIDDEN, "account disabled");
    }

    /**
     * Issues a browser session for a ticket owner.
     *
     * @param userId account id already proven by the ticket
     * @param source source platform recorded with the refresh session
     * @return a new token pair
     * @throws BizException {@code 40101} when the account is missing, {@code 67001} when it was
     *     cancelled, {@code 40301} when it is disabled
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public RefreshResponse issueSession(long userId, String source) {
        UserAccount account = authUserMapper.selectById(userId);
        if (account == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        requireLoginAllowed(account);
        List<String> roles = authUserMapper.selectRoleCodes(userId);
        List<String> permissions = authUserMapper.selectPermissionCodes(userId);
        AuthTokens tokens = jwtTokenService.issue(userId, roles, permissions);
        refreshTokenStore.store(
                tokens.getRefreshToken(), new RefreshSession(userId, Instant.now(), source));
        return new RefreshResponse(
                tokens.getAccessToken(), tokens.getRefreshToken(), tokens.getExpiresInSeconds());
    }
}
