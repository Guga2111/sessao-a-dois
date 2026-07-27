package com.app.auth;

import java.util.UUID;

public record UserSummary(UUID id, String name, String email) {
}
