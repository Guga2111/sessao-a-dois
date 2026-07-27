package com.app.tracking;

import com.app.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "user_review", uniqueConstraints = @UniqueConstraint(columnNames = {"media_track_id", "user_id"}))
public class UserReview {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "media_track_id", nullable = false)
	private MediaTrack mediaTrack;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column
	private Integer rating;

	@Column(columnDefinition = "TEXT")
	private String opinion;

	protected UserReview() {
	}

	public UserReview(MediaTrack mediaTrack, User user, Integer rating, String opinion) {
		this.mediaTrack = mediaTrack;
		this.user = user;
		this.rating = rating;
		this.opinion = opinion;
	}

	public UUID getId() {
		return id;
	}

	public MediaTrack getMediaTrack() {
		return mediaTrack;
	}

	public User getUser() {
		return user;
	}

	public Integer getRating() {
		return rating;
	}

	public void setRating(Integer rating) {
		this.rating = rating;
	}

	public String getOpinion() {
		return opinion;
	}

	public void setOpinion(String opinion) {
		this.opinion = opinion;
	}
}
