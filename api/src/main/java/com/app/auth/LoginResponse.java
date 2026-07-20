package com.app.auth;

public record LoginResponse(String token, UserSummary user, Object couple) {
}
