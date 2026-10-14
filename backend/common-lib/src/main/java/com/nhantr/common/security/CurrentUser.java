package com.nhantr.common.security;

public record CurrentUser(Long id, String role) {
    public boolean isAdmin() {
        return role != null && (role.equals("MODERATOR") || role.equals("SUPER_ADMIN"));
    }
}
