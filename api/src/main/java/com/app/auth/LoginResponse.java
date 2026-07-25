package com.app.auth;

import com.app.couple.CoupleResponse;

public record LoginResponse(String token, UserSummary user, CoupleResponse couple) {
}
