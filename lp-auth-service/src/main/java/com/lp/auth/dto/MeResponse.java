package com.lp.auth.dto;

import java.util.List;

/**
 * Current user profile with effective roles and permissions.
 *
 * <p>Returned by {@code GET /api/v1/auth/me}. The payload never carries a password hash, and
 * permissions are the enabled {@code sys_menu.permission_code} values reachable through the user's
 * roles.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class MeResponse {

    private final long id;

    private final String username;

    private final String nickname;

    private final String avatarUrl;

    private final String email;

    private final String phone;

    private final List<String> roles;

    private final List<String> permissions;

    /**
     * Creates an immutable profile.
     *
     * @param id user id
     * @param username login name
     * @param nickname display name
     * @param avatarUrl avatar address, may be {@code null}
     * @param email email address, may be {@code null}
     * @param phone phone number, may be {@code null}
     * @param roles role codes, may be empty
     * @param permissions permission codes, may be empty
     * @author Caspian
     * @since 0.1.0
     */
    public MeResponse(
            long id,
            String username,
            String nickname,
            String avatarUrl,
            String email,
            String phone,
            List<String> roles,
            List<String> permissions) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.email = email;
        this.phone = phone;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
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
     * Returns the login name.
     *
     * @return username
     * @author Caspian
     * @since 0.1.0
     */
    public String getUsername() {
        return username;
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
     * Returns the email address.
     *
     * @return email, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getEmail() {
        return email;
    }

    /**
     * Returns the phone number.
     *
     * @return phone, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Returns the effective role codes.
     *
     * @return immutable role list
     * @author Caspian
     * @since 0.1.0
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * Returns the effective permission codes.
     *
     * @return immutable permission list
     * @author Caspian
     * @since 0.1.0
     */
    public List<String> getPermissions() {
        return permissions;
    }
}
