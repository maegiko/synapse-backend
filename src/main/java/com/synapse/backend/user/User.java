package com.synapse.backend.user;

import java.time.LocalDateTime;

import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "total_flashcards_reviewed", nullable = false)
    private long totalFlashcardsReviewed;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "updated_at", nullable = false, insertable = false)
    private LocalDateTime updatedAt;

    protected User() {}

    public User(String name, String email, String passwordHash, String timeZone) {
        this.fullName = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.timeZone = timeZone;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public long getTotalFlashcardsReviewed() {
        return totalFlashcardsReviewed;
    }

    /** The user's IANA time zone, which every calendar-day calculation is made in. */
    public String getTimeZone() {
        return timeZone;
    }

    /** When the user confirmed their email address, or null while the account is unverified. */
    public LocalDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void markEmailVerified(LocalDateTime verifiedAt) {
        this.emailVerifiedAt = verifiedAt;
    }

    public void updateFullName(String fullName) {
        this.fullName = fullName;
    }

    public void updateEmail(String email) {
        this.email = email;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void updateTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

}
