package com.app.auth;

import com.app.couple.CoupleResponse;

public record LoginResponse(UserSummary user, CoupleResponse couple) {
}
