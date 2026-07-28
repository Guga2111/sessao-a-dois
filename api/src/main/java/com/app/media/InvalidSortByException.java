package com.app.media;

public class InvalidSortByException extends RuntimeException {

	public InvalidSortByException(String sortBy) {
		super("Valor de ordenacao invalido: '" + sortBy
			+ "'. Use popularity.desc, popularity.asc, vote_average.desc, vote_average.asc, "
			+ "release_date.desc ou release_date.asc.");
	}
}
