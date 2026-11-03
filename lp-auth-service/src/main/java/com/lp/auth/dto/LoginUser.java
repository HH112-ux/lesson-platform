package com.lp.auth.dto;

import java.util.List;

/**
 * User summary embedded in a login response.
 *
 * <p>Never contains the password hash, email or phone: the login response is not an identity
 * document. Role codes come from {@code sys_role}.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public class LoginUser {

    private final long id;

    private final String nickname;

    private final String avatarUrl;

    private final List<String> roles;

    /**
     * Creates an immutable summary.
     *
     * @param id        user id
     * @param nickname  display name
     * @param avatarUrl avatar address, may be {@code null}
     * @param roles     role codes, may be empty
     * @author Caspian
     * @since 0.1.0
     */
    public LoginUser(long id, String nickname, String avatarUrl, List<String> roles) {
        this.id = id;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /**
     * Returns the user id.
     *
     * @return user id
     * @author Caspian
     * @since 0.1.0
     */
    public long getId() {
        return id;
    }

    /**
     * Returns the display name.
     *
     * @return nickname
     * @author Caspian
     * @since 0.1.0
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Returns the avatar address.
     *
     * @return avatar URL, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getAvatarUrl() {
        return avatarUrl;
    }

    /**
     * Returns the role codes.
     *
     * @return immutable role list
     * @author Caspian
     * @since 0.1.0
     */
    public List<String> getRoles() {
        return roles;
    }
}
