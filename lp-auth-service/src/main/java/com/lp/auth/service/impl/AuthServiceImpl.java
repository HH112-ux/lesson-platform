package com.lp.auth.service.impl;

import com.lp.auth.dto.LoginRequest;
import com.lp.auth.dto.LoginResponse;
import com.lp.auth.dto.LoginUser;
import com.lp.auth.entity.UserAccount;
import com.lp.auth.mapper.AuthUserMapper;
import com.lp.auth.security.AuthTokens;
import com.lp.auth.security.JwtTokenService;
import com.lp.auth.service.AuthService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Verifies credentials against {@code lp_ums} and issues tokens.
 *
 * <p>Missing account and wrong password share {@code 40101} so the response never reveals
 * whether an account exists. A non active account returns {@code 40301} after the password
 * check, which keeps disabled accounts from probing passwords.</p>
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

    private final PasswordEncoder passwordEncoder;

    /**
     * Creates the service with its collaborators.
     *
     * @param authUserMapper  account and role queries
     * @param jwtTokenService token issuer
     * @param passwordEncoder BCrypt verifier
     * @author Caspian
     * @since 0.1.0
     */
    public AuthServiceImpl(AuthUserMapper authUserMapper, JwtTokenService jwtTokenService,
                           PasswordEncoder passwordEncoder) {
        this.authUserMapper = authUserMapper;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verifies the account password and issues a token pair.
     *
     * @param request validated login credentials
     * @return tokens plus the authenticated user summary
     * @throws BizException {@code 40101} when the account is missing or the password is wrong,
     *                      {@code 40301} when the account is not active
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public LoginResponse login(LoginRequest request) {
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
        LoginUser user = new LoginUser(account.getId(), account.getNickname(),
                account.getAvatarUrl(), roles);
        return new LoginResponse(tokens.getAccessToken(), tokens.getRefreshToken(),
                tokens.getExpiresInSeconds(), user);
    }
}
