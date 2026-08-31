package com.synapse.backend.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;

import com.synapse.backend.analytics.dto.DueForecastResponse;
import com.synapse.backend.analytics.dto.MasteryCountsResponse;
import com.synapse.backend.analytics.dto.QuizAttemptResponse;
import com.synapse.backend.analytics.dto.RatingCount;
import com.synapse.backend.flashcards.enums.ReviewRating;
import com.synapse.backend.flashcards.repositories.DailyDeckReviewProjection;
import com.synapse.backend.flashcards.repositories.FlashcardDeckRepository;
import com.synapse.backend.flashcards.repositories.FlashcardDeckReviewRepository;
import com.synapse.backend.quiz.repositories.DailyQuizAttemptProjection;
import com.synapse.backend.quiz.repositories.QuizAttemptTotalsProjection;
import com.synapse.backend.quiz.repositories.QuizScoreRepository;
import com.synapse.backend.user.UserRepository;

/**
 * The database side of analytics: aggregates are counted and grouped in SQL rather than by
 * loading a user's whole review and score history into memory.
 *
 * <p>It holds the flashcard, quiz, and user repositories because analytics reads across all
 * three, the same way {@code GroupPersistenceService} holds the repositories of everything a
 * group can contain.</p>
 *
 * <p>Every method takes the window as local dates and converts it here. Timestamps are stored
 * as UTC, so a local day is turned into the half-open UTC range that covers it before any
 * comparison; the daily rollups convert the other way, back into the user's zone, so a review
 * lands on the day the user experienced it.</p>
 */
@Service
public class AnalyticsPersistenceService {

    /** Latest rating of a deck the user is not recalling well. */
    private static final List<ReviewRating> STRUGGLING_RATINGS = List.of(ReviewRating.AGAIN, ReviewRating.HARD);

    /** Latest rating of a deck the user is recalling well. */
    private static final List<ReviewRating> RECALLED_RATINGS = List.of(ReviewRating.GOOD, ReviewRating.EASY);

    /** The interval at which a well recalled deck counts as strong rather than still being learned. */
    private static final int STRONG_INTERVAL_DAYS = 21;

    private final FlashcardDeckReviewRepository flashcardDeckReviewRepository;
    private final FlashcardDeckRepository flashcardDeckRepository;
    private final QuizScoreRepository quizScoreRepository;
    private final UserRepository userRepository;

    public AnalyticsPersistenceService(
        FlashcardDeckReviewRepository flashcardDeckReviewRepository,
        FlashcardDeckRepository flashcardDeckRepository,
        QuizScoreRepository quizScoreRepository,
        UserRepository userRepository
    ) {
        this.flashcardDeckReviewRepository = flashcardDeckReviewRepository;
        this.flashcardDeckRepository = flashcardDeckRepository;
        this.quizScoreRepository = quizScoreRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns the user's deck-review totals for each local day in the window.
     *
     * @param userId the id of the authenticated user.
     * @param zone the user's time zone.
     * @param from the first local day of the window.
     * @param to the last local day of the window.
     * @return one row per day that had a review, oldest first.
     */
    public List<DailyDeckReviewProjection> getDailyDeckReviews(
        Long userId,
        ZoneId zone,
        LocalDate from,
        LocalDate to
    ) {
        return flashcardDeckReviewRepository.findDailyTotals(
            userId,
            zone.getId(),
            startOfDayUtc(from, zone),
            startOfDayUtc(to.plusDays(1), zone)
        );
    }

    /**
     * Returns how the window's reviews were rated.
     *
     * @param userId the id of the authenticated user.
     * @param zone the user's time zone.
     * @param from the first local day of the window.
     * @param to the last local day of the window.
     * @return one row per rating that occurs in the window.
     */
    public List<RatingCount> getRatingCounts(Long userId, ZoneId zone, LocalDate from, LocalDate to) {
        return flashcardDeckReviewRepository.countRatings(
            userId,
            startOfDayUtc(from, zone),
            startOfDayUtc(to.plusDays(1), zone)
        );
    }

    /**
     * Returns the user's quiz-attempt totals for each local day in the window.
     *
     * @param userId the id of the authenticated user.
     * @param zone the user's time zone.
     * @param from the first local day of the window.
     * @param to the last local day of the window.
     * @return one row per day that had an attempt, oldest first.
     */
    public List<DailyQuizAttemptProjection> getDailyQuizAttempts(
        Long userId,
        ZoneId zone,
        LocalDate from,
        LocalDate to
    ) {
        return quizScoreRepository.findDailyTotals(
            userId,
            zone.getId(),
            startOfDayUtc(from, zone),
            startOfDayUtc(to.plusDays(1), zone)
        );
    }

    /**
     * Returns the window's whole-period quiz totals.
     *
     * @param userId the id of the authenticated user.
     * @param zone the user's time zone.
     * @param from the first local day of the window.
     * @param to the last local day of the window.
     * @return the totals, with null averages when the window holds no attempts.
     */
    public QuizAttemptTotalsProjection getQuizTotals(Long userId, ZoneId zone, LocalDate from, LocalDate to) {
        return quizScoreRepository.findTotals(
            userId,
            startOfDayUtc(from, zone),
            startOfDayUtc(to.plusDays(1), zone)
        );
    }

    /**
     * Returns every quiz attempt in the window, oldest first.
     *
     * @param userId the id of the authenticated user.
     * @param zone the user's time zone.
     * @param from the first local day of the window.
     * @param to the last local day of the window.
     * @return the attempts in chronological order.
     */
    public List<QuizAttemptResponse> getQuizAttempts(Long userId, ZoneId zone, LocalDate from, LocalDate to) {
        return quizScoreRepository.findAttempts(
            userId,
            startOfDayUtc(from, zone),
            startOfDayUtc(to.plusDays(1), zone)
        );
    }

    /**
     * Returns how many decks are past due and how many fall due today.
     *
     * <p>These describe the library as it stands rather than the window, so they are counted
     * against the user's today rather than the window's end.</p>
     *
     * @param userId the id of the authenticated user.
     * @param today the user's current local date.
     * @return the overdue count.
     */
    public long countOverdueDecks(Long userId, LocalDate today) {
        return flashcardDeckRepository.countByUserIdAndNextReviewDateLessThan(userId, today);
    }

    /**
     * Returns how many decks fall due today.
     *
     * @param userId the id of the authenticated user.
     * @param today the user's current local date.
     * @return the count of decks due today.
     */
    public long countDecksDueToday(Long userId, LocalDate today) {
        return flashcardDeckRepository.countByUserIdAndNextReviewDate(userId, today);
    }

    /**
     * Returns how many decks fall due on each day of a forward window.
     *
     * @param userId the id of the authenticated user.
     * @param from the first day of the forecast.
     * @param to the last day of the forecast.
     * @return one row per day that has at least one deck due, earliest first.
     */
    public List<DueForecastResponse> getDueForecast(Long userId, LocalDate from, LocalDate to) {
        return flashcardDeckRepository.countDecksDueBetween(userId, from, to);
    }

    /**
     * Buckets the user's decks by how well they are known.
     *
     * <p>Three counting queries rather than one grouped one, because the buckets are defined
     * by different columns: struggling by the latest rating alone, and the other two by that
     * rating together with the current interval. A never-reviewed deck has no latest rating
     * and falls into none of them.</p>
     *
     * @param userId the id of the authenticated user.
     * @return the struggling, learning, and strong counts.
     */
    public MasteryCountsResponse getMasteryCounts(Long userId) {
        return new MasteryCountsResponse(
            flashcardDeckRepository.countByUserIdAndLastRatingIn(userId, STRUGGLING_RATINGS),
            flashcardDeckRepository.countByUserIdAndLastRatingInAndIntervalDaysLessThan(
                userId,
                RECALLED_RATINGS,
                STRONG_INTERVAL_DAYS
            ),
            flashcardDeckRepository.countByUserIdAndLastRatingInAndIntervalDaysGreaterThanEqual(
                userId,
                RECALLED_RATINGS,
                STRONG_INTERVAL_DAYS
            )
        );
    }

    /**
     * Returns the user's lifetime cards-reviewed counter.
     *
     * @param userId the id of the authenticated user.
     * @return the lifetime total, which deletions never reduce.
     */
    public long getLifetimeCardsReviewed(Long userId) {
        return userRepository.findTotalFlashcardsReviewedById(userId);
    }

    /**
     * The UTC instant a local day begins at, as the wall-clock value the timestamp columns hold.
     *
     * @param date the local date.
     * @param zone the user's time zone, whose own rules decide the offset on that date.
     * @return the equivalent UTC date-time.
     */
    private LocalDateTime startOfDayUtc(LocalDate date, ZoneId zone) {
        return LocalDateTime.ofInstant(date.atStartOfDay(zone).toInstant(), ZoneOffset.UTC);
    }

}
