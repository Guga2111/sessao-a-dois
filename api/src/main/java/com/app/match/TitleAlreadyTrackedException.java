package com.app.match;

public class TitleAlreadyTrackedException extends RuntimeException {

	public TitleAlreadyTrackedException() {
		super("titulo ja esta em uma lista do casal");
	}
}
