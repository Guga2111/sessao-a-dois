package com.app.tracking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpsertReviewRequest(

	@Min(value = 1, message = "rating deve ser entre 1 e 5")
	@Max(value = 5, message = "rating deve ser entre 1 e 5")
	Integer rating,

	String opinion
) {
}
