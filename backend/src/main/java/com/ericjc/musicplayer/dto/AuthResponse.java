package com.ericjc.musicplayer.dto;

import com.ericjc.musicplayer.entity.User;

public class AuthResponse {

    private String token;
    private String username;
    private Long userId;
    private String role;
    private String nickname;
    private String avatarUrl;
    private String bio;

    public AuthResponse(String token, String username, Long userId, String role,
                        String nickname, String avatarUrl, String bio) {
        this.token = token;
        this.username = username;
        this.userId = userId;
        this.role = role;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
    }

    public static AuthResponse fromUser(String token, User user) {
        return new AuthResponse(
                token,
                user.getUsername(),
                user.getId(),
                user.getRole(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getBio()
        );
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}
