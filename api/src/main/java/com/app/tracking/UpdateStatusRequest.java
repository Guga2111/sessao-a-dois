package com.app.tracking;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdateStatusRequest(

	@NotNull(message = "status nao pode ser vazio")
	MediaStatus status,

	LocalDate watchedDate
) {
}
