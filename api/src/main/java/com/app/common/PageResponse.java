package com.app.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Explicit JSON contract for paginated responses, replacing {@code Page<T>} directly on the
 * wire (Spring warns that {@code PageImpl} JSON serialization isn't guaranteed stable). Field
 * set/order is fixed to what the client already mirrors: content, totalElements, totalPages,
 * number, size.
 */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
				page.getNumber(), page.getSize());
	}
}
