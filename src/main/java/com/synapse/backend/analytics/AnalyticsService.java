package com.synapse.backend.analytics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

import org.springframework.stereotype.Service;

import com.synapse.backend.analytics.dto.AnalyticsOverviewResponse;
import com.synapse.backend.analytics.dto.AnalyticsPeriodResponse;
import com.synapse.backend.analytics.dto.AnalyticsResponse;
import com.synapse.backend.analytics.dto.ConsistencyAnalyticsResponse;
import com.synapse.backend.analytics.dto.DailyActivityResponse;
import com.synapse.backend.analytics.dto.DueForecastResponse;
import com.synapse.backend.analytics.dto.FlashcardAnalyticsResponse;
import com.synapse.backend.analytics.dto.FlashcardDayResponse;
import com.synapse.backend.analytics.dto.QuizAnalyticsResponse;
import com.synapse.backend.analytics.dto.QuizAttemptResponse;
import com.synapse.backend.analytics.dto.QuizDayResponse;
import com.synapse.backend.analytics.dto.RatingCount;
import com.synapse.backend.analytics.dto.RatingCountsResponse;
import com.synapse.backend.analytics.exceptions.InvalidAnalyticsPeriodException;
import com.synapse.backend.flashcards.enums.ReviewRating;
import com.synapse.backend.flashcards.repositories.DailyDeckReviewProjection;
import com.synapse.backend.quiz.repositories.DailyQuizAttemptProjection;
import com.synapse.backend.quiz.repositories.QuizAttemptTotalsProjection;
import com.synapse.backend.streak.StreakService;
import com.synapse.backend.streak.dto.StreakResponse;
import com.synapse.backend.user.UserTimeZoneService;

/**
 * Assembles a user's study analytics from the aggregates the persistence service counts.
 *
 * <p>The window is whole calendar days in the user's own time zone, ending on their today, so
 * two users acting at the same instant in different zones can be on different days. Stored
 * timestamps stay UTC; only the grouping and the range are user-local.</p>
 */
@Service
public class AnalyticsService {

    /** The windows the endpoint offers. Anything else is a client mistake rather than a clamp. */
    private static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 30, 90, 365);

    /** Days covered by the due forecast, counting today as the first of them. */
    private static final int FORECAST_DAYS = 7;

    private final AnalyticsPersistenceService persistenceService;
    private final UserTimeZoneService userTimeZoneService;
    private final StreakService streakService;
    private final Clock clock;

    public AnalyticsService(
        AnalyticsPersistenceService persistenceService,
        UserTimeZoneService userTimeZoneService,
        StreakService streakService,
        Clock clock
    ) {
        this.persistenceService = persistenceService;
        this.userTimeZoneService = userTimeZoneService;
        this.streakService = streakService;
        this.clock = clock;
    }

    /**
     * Returns the user's study analytics for a window ending today.
     *
     * @param userId the id of the authenticated user.
     * @param days how many calendar days to cover; one of 7, 30, 90, or 365.
     * @return the period, overview, flashcard, quiz, consistency, and daily figures.
     * @throws InvalidAnalyticsPeriodException if the period is not one of the offered windows.
     * @throws UserNotFoundException if the user ID does not exist in DB.
     */
    public AnalyticsResponse getAnalytics(Long userId, int days) {
        if (!ALLOWED_PERIODS.contains(days))
            throw new InvalidAnalyticsPeriodException("period: must be one of 7, 30, 90, or 365");

        ZoneId zone = userTimeZoneService.zoneOf(userId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        LocalDate from = today.minusDays(days - 1L);

        List<DailyActivityResponse> dailyActivity = buildDailyActivity(userId, zone, from, today, days);
        List<QuizAttemptResponse> attempts = persistenceService.getQuizAttempts(userId, zone, from, today);
        QuizAttemptTotalsProjection quizTotals = persistenceService.getQuizTotals(userId, zone, from, today);

        return new AnalyticsResponse(
            new AnalyticsPeriodResponse(days, from, today),
            buildOverview(userId, dailyActivity, quizTotals),
            buildFlashcards(userId, zone, from, today, dailyActivity),
            buildQuizzes(quizTotals, dailyActivity, attempts),
            buildConsistency(userId, dailyActivity),
            dailyActivity
        );
    }

    /**
     * Builds one entry for every day of the window, including the empty ones, by merging the
     * deck-review and quiz rollups onto a dense calendar.
     */
    private List<DailyActivityResponse> buildDailyActivity(
        Long userId,
        ZoneId zone,
        LocalDate from,
        LocalDate to,
        int days
    ) {
        Map<LocalDate, DailyDeckReviewProjection> reviewsByDay = new HashMap<>();
        for (DailyDeckReviewProjection row : persistenceService.getDailyDeckReviews(userId, zone, from, to))
            reviewsByDay.put(row.getDay(), row);

        Map<LocalDate, DailyQuizAttemptProjection> attemptsByDay = new HashMap<>();
        for (DailyQuizAttemptProjection row : persistenceService.getDailyQuizAttempts(userId, zone, from, to))
            attemptsByDay.put(row.getDay(), row);

        List<DailyActivityResponse> dailyActivity = new ArrayList<>();

        for (int offset = 0; offset < days; offset++) {
            LocalDate day = from.plusDays(offset);
            DailyDeckReviewProjection reviews = reviewsByDay.get(day);
            DailyQuizAttemptProjection attempts = attemptsByDay.get(day);

            long studySeconds = (reviews == null ? 0 : reviews.getStudySeconds())
                + (attempts == null ? 0 : attempts.getStudySeconds());

            dailyActivity.add(new DailyActivityResponse(
                day,
                studySeconds,
                reviews == null ? 0 : reviews.getCardsReviewed(),
                reviews == null ? 0 : reviews.getReviewSessions(),
                attempts == null ? 0 : attempts.getAttempts()
            ));
        }

        return dailyActivity;
    }

    private AnalyticsOverviewResponse buildOverview(
        Long userId,
        List<DailyActivityResponse> dailyActivity,
        QuizAttemptTotalsProjection quizTotals
    ) {
        long totalStudySeconds = sum(dailyActivity, DailyActivityResponse::studySeconds);
        int activeDays = countActiveDays(dailyActivity);

        return new AnalyticsOverviewResponse(
            totalStudySeconds,
            activeDays,
            dailyActivity.size() - activeDays,
            activeDays == 0 ? 0 : Math.round((double) totalStudySeconds / activeDays),
            sum(dailyActivity, DailyActivityResponse::cardsReviewed),
            persistenceService.getLifetimeCardsReviewed(userId),
            sum(dailyActivity, DailyActivityResponse::deckReviews),
            quizTotals.getAttempts(),
            quizTotals.getAveragePercentage()
        );
    }

    private FlashcardAnalyticsResponse buildFlashcards(
        Long userId,
        ZoneId zone,
        LocalDate from,
        LocalDate today,
        List<DailyActivityResponse> dailyActivity
    ) {
        RatingCountsResponse ratings = foldRatings(persistenceService.getRatingCounts(userId, zone, from, today));
        long ratedReviews = ratings.again() + ratings.hard() + ratings.good() + ratings.easy();

        List<FlashcardDayResponse> perDay = dailyActivity
            .stream()
            .filter(day -> day.deckReviews() > 0)
            .map(day -> new FlashcardDayResponse(day.date(), day.cardsReviewed(), day.deckReviews()))
            .toList();

        return new FlashcardAnalyticsResponse(
            sum(dailyActivity, DailyActivityResponse::cardsReviewed),
            sum(dailyActivity, DailyActivityResponse::deckReviews),
            perDay,
            ratings,
            ratedReviews == 0 ? null : (double) (ratings.good() + ratings.easy()) / ratedReviews,
            persistenceService.countOverdueDecks(userId, today),
            persistenceService.countDecksDueToday(userId, today),
            buildDueForecast(userId, today),
            persistenceService.getMasteryCounts(userId)
        );
    }

    /** Fills the days nothing is due on, so the forecast is a complete week rather than a sparse one. */
    private List<DueForecastResponse> buildDueForecast(Long userId, LocalDate today) {
        LocalDate lastDay = today.plusDays(FORECAST_DAYS - 1L);

        Map<LocalDate, Long> dueByDay = new HashMap<>();
        for (DueForecastResponse row : persistenceService.getDueForecast(userId, today, lastDay))
            dueByDay.put(row.date(), row.deckCount());

        List<DueForecastResponse> forecast = new ArrayList<>();

        for (int offset = 0; offset < FORECAST_DAYS; offset++) {
            LocalDate day = today.plusDays(offset);
            forecast.add(new DueForecastResponse(day, dueByDay.getOrDefault(day, 0L)));
        }

        return forecast;
    }

    private QuizAnalyticsResponse buildQuizzes(
        QuizAttemptTotalsProjection quizTotals,
        List<DailyActivityResponse> dailyActivity,
        List<QuizAttemptResponse> attempts
    ) {
        List<QuizDayResponse> perDay = dailyActivity
            .stream()
            .filter(day -> day.quizAttempts() > 0)
            .map(day -> new QuizDayResponse(day.date(), day.quizAttempts()))
            .toList();

        return new QuizAnalyticsResponse(
            quizTotals.getAttempts(),
            quizTotals.getDistinctQuizzes(),
            perDay,
            quizTotals.getAveragePercentage(),
            quizTotals.getBestPercentage(),
            quizTotals.getAverageDurationSeconds(),
            attempts,
            calculateImprovement(attempts)
        );
    }

    /**
     * How much the user's results moved over the window.
     *
     * <p>For each quiz attempted at least twice, the latest percentage minus the first; the
     * result is the mean of those, in percentage points. A quiz attempted once shows no trend
     * and is left out, and a window where nothing was attempted twice has no answer at all.</p>
     *
     * <p>Derived from the attempts already loaded for the score history rather than queried
     * again, since the endpoint returns them either way.</p>
     */
    private Double calculateImprovement(List<QuizAttemptResponse> attempts) {
        Map<String, List<QuizAttemptResponse>> byQuiz = new LinkedHashMap<>();
        for (QuizAttemptResponse attempt : attempts)
            byQuiz.computeIfAbsent(attempt.quizId(), quizId -> new ArrayList<>()).add(attempt);

        double totalChange = 0;
        int quizzesWithTrend = 0;

        for (List<QuizAttemptResponse> quizAttempts : byQuiz.values()) {
            if (quizAttempts.size() < 2)
                continue;

            totalChange += quizAttempts.get(quizAttempts.size() - 1).percentage() - quizAttempts.get(0).percentage();
            quizzesWithTrend++;
        }

        return quizzesWithTrend == 0 ? null : totalChange / quizzesWithTrend;
    }

    private ConsistencyAnalyticsResponse buildConsistency(Long userId, List<DailyActivityResponse> dailyActivity) {
        StreakResponse streak = streakService.getStreak(userId);
        int activeDays = countActiveDays(dailyActivity);
        long sessions = sum(dailyActivity, DailyActivityResponse::deckReviews)
            + sum(dailyActivity, DailyActivityResponse::quizAttempts);

        return new ConsistencyAnalyticsResponse(
            streak.currentStreak(),
            streak.longestStreak(),
            activeDays,
            dailyActivity.size() - activeDays,
            activeDays == 0 ? 0 : (double) sessions / activeDays,
            longestInactivityGap(dailyActivity)
        );
    }

    /** The longest run of consecutive days in the window with neither a review nor an attempt. */
    private int longestInactivityGap(List<DailyActivityResponse> dailyActivity) {
        int longestGap = 0;
        int gap = 0;

        for (DailyActivityResponse day : dailyActivity) {
            gap = isActive(day) ? 0 : gap + 1;
            longestGap = Math.max(longestGap, gap);
        }

        return longestGap;
    }

    private RatingCountsResponse foldRatings(List<RatingCount> counts) {
        Map<ReviewRating, Long> byRating = new HashMap<>();
        for (RatingCount count : counts)
            byRating.put(count.rating(), count.total());

        return new RatingCountsResponse(
            byRating.getOrDefault(ReviewRating.AGAIN, 0L),
            byRating.getOrDefault(ReviewRating.HARD, 0L),
            byRating.getOrDefault(ReviewRating.GOOD, 0L),
            byRating.getOrDefault(ReviewRating.EASY, 0L)
        );
    }

    /** A day counts as active when the user reviewed a deck or attempted a quiz on it. */
    private boolean isActive(DailyActivityResponse day) {
        return day.deckReviews() > 0 || day.quizAttempts() > 0;
    }

    private int countActiveDays(List<DailyActivityResponse> dailyActivity) {
        return (int) dailyActivity.stream().filter(this::isActive).count();
    }

    private long sum(List<DailyActivityResponse> dailyActivity, ToLongFunction<DailyActivityResponse> field) {
        return dailyActivity.stream().mapToLong(field).sum();
    }

}
