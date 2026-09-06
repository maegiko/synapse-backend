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

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

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
        this(name, email, passwordHash, null, timeZone);
    }

    /**
     * An account that may sign in with a password, with Google, or with both. At least one
     * of them has to be present: the database refuses a row that has neither.
     */
    public User(String name, String email, String passwordHash, String googleSubject, String timeZone) {
        this.fullName = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.googleSubject = googleSubject;
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

    /** The BCrypt hash of the user's password, or null for an account that only signs in with Google. */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** Google's stable subject claim for the linked Google Account, or null if none is linked. */
    public String getGoogleSubject() {
        return googleSubject;
    }

    public boolean hasPassword() {
        return passwordHash != null;
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

    public void linkGoogleSubject(String googleSubject) {
        this.googleSubject = googleSubject;
    }

    public void unlinkGoogleSubject() {
        this.googleSubject = null;
    }

    /**
     * Drops a password that was chosen before anybody proved they owned the address.
     *
     * <p>Only used when Google claims an account that registered but never verified,
     * where the stored password may belong to somebody who preregistered the victim's
     * address. The account keeps a way in because the Google subject is linked in the
     * same transaction; the database check constraint refuses the write otherwise.</p>
     */
    public void clearPasswordHash() {
        this.passwordHash = null;
    }

    public void updateTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

}
