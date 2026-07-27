package com.app.tracking;

import java.util.UUID;

public record ReviewDto(UUID userId, String userName, Integer rating, String opinion) {
}
