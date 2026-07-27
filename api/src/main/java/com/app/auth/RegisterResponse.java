package com.app.auth;

import java.util.UUID;

public record RegisterResponse(UUID id, String name, String email) {
}
