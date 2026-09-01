package com.synapse.backend.flashcards;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.synapse.backend.flashcards.dto.AddFlashcardRequest;
import com.synapse.backend.flashcards.dto.AddFlashcardResponse;
import com.synapse.backend.flashcards.dto.FlashcardResponse;
import com.synapse.backend.flashcards.dto.generate.FlashcardSourceNote;
import com.synapse.backend.flashcards.dto.list.FlashcardListResponse;
import com.synapse.backend.flashcards.dto.list.FlashcardWithIdResponse;
import com.synapse.backend.flashcards.dto.list.SingleDeckResponse;
import com.synapse.backend.flashcards.dto.review.ReviewDeckResponse;
import com.synapse.backend.flashcards.dto.review.ReviewQueueDeckResponse;
import com.synapse.backend.flashcards.dto.review.ReviewQueueResponse;
import com.synapse.backend.flashcards.entities.Flashcard;
import com.synapse.backend.flashcards.entities.FlashcardDeck;
import com.synapse.backend.flashcards.entities.FlashcardDeckReview;
import com.synapse.backend.flashcards.enums.ReviewRating;
import com.synapse.backend.flashcards.exceptions.DeckNotFound;
import com.synapse.backend.flashcards.exceptions.EmptyDeckException;
import com.synapse.backend.flashcards.exceptions.FlashcardNotFound;
import com.synapse.backend.flashcards.repositories.FlashcardDeckRepository;
import com.synapse.backend.flashcards.repositories.FlashcardDeckReviewRepository;
import com.synapse.backend.flashcards.repositories.FlashcardRepository;
import com.synapse.backend.groups.repositories.StudyGroupRepository;
import com.synapse.backend.user.UserRepository;
import com.synapse.backend.user.UserTimeZoneService;

import jakarta.transaction.Transactional;

@Service
public class FlashcardPersistenceService {
    private static final BigDecimal MINIMUM_EASE_FACTOR = new BigDecimal("1.30");
    private static final double HARD_INTERVAL_MULTIPLIER = 1.2;
    private static final double EASY_INTERVAL_MULTIPLIER = 1.3;
    private static final int EASY_MINIMUM_INTERVAL_DAYS = 4;
    private static final int GOOD_SECOND_INTERVAL_DAYS = 6;

    private final FlashcardDeckRepository flashcardDeckRepository;
    private final FlashcardRepository flashcardRepository;
    private final FlashcardDeckReviewRepository flashcardDeckReviewRepository;
    private final UserRepository userRepository;
    private final StudyGroupRepository studyGroupRepository;
    private final Clock clock;
    private final UserTimeZoneService userTimeZoneService;

    public FlashcardPersistenceService(
        FlashcardDeckRepository flashcardDeckRepository,
        FlashcardRepository flashcardRepository,
        FlashcardDeckReviewRepository flashcardDeckReviewRepository,
        UserRepository userRepository,
        StudyGroupRepository studyGroupRepository,
        Clock clock,
        UserTimeZoneService userTimeZoneService
    ) {
        this.flashcardDeckRepository = flashcardDeckRepository;
        this.flashcardRepository = flashcardRepository;
        this.flashcardDeckReviewRepository = flashcardDeckReviewRepository;
        this.userRepository = userRepository;
        this.studyGroupRepository = studyGroupRepository;
        this.clock = clock;
        this.userTimeZoneService = userTimeZoneService;
    }

    /**
     * Saves a deck and flashcards generated from a note summary to the DB.
     *
     * @param flashcards list of flashcards to save.
     * @param userId the id of the currently authenticated user.
     * @param note the note that the flashcards were generated from.
     * @return the deck id if available, else null.
     */
    @Transactional
    public String saveFlashcardFromNote(List<FlashcardResponse> flashcards, Long userId, FlashcardSourceNote note) {
        if (flashcards == null)
            return null;

        FlashcardDeck flashcardDeck = new FlashcardDeck(userId, note.id(), note.title(), "NOTE");
        FlashcardDeck newFlashcardDeck = flashcardDeckRepository.save(flashcardDeck);

        List<Flashcard> newFlashcards = new ArrayList<>();

        for (int i = 0; i < flashcards.size(); i++) {
            FlashcardResponse flashcard = flashcards.get(i);
            newFlashcards.add(
                new Flashcard(newFlashcardDeck.getId(), flashcard.title(), flashcard.answer(), i)
            );
        }

        flashcardRepository.saveAll(newFlashcards);

        return newFlashcardDeck.getPublicId();
    }

    /**
     * Returns a page of flashcard decks owned by user, optionally filtered by deck title.
     *
     * @param userId the id of the currently authenticated user.
     * @param query an optional case-insensitive partial title search, or null/blank for no search.
     * @param pageable the page to return.
     * @return the requested page of decks with their cards and its pagination metadata.
     */
    public FlashcardListResponse getAllFlashcards(Long userId, String query, Pageable pageable) {
        Page<FlashcardDeck> decks = findDecksPage(userId, query, pageable);

        if (decks.isEmpty())
            return toFlashcardListResponse(List.of(), decks);

        List<Long> deckIds = decks.stream().map(FlashcardDeck::getId).toList();

        Map<Long, List<Flashcard>> flashcards = flashcardRepository.findByDeckIdInOrderByDeckIdAscPositionAsc(deckIds)
            .stream()
            .collect(Collectors.groupingBy(Flashcard::getDeckId));

        List<SingleDeckResponse> flashcardList = new ArrayList<>();

        for (FlashcardDeck deck : decks) {
            List<FlashcardWithIdResponse> cards = flashcards
                .getOrDefault(deck.getId(), List.of())
                .stream()
                .map(c -> new FlashcardWithIdResponse(c.getPublicId(), c.getQuestion(), c.getAnswer()))
                .toList();

            flashcardList.add(
                new SingleDeckResponse(
                    deck.getPublicId(),
                    deck.getTitle(),
                    cards,
                    groupPublicId(deck.getGroupId()),
                    deck.isPinned()
                )
            );
        }

        return toFlashcardListResponse(flashcardList, decks);
    }

    private Page<FlashcardDeck> findDecksPage(Long userId, String query, Pageable pageable) {
        String search = query == null ? "" : query.trim();

        if (search.isEmpty())
            return flashcardDeckRepository.findByUserIdOrderByPinnedDescCreatedAtDescIdDesc(userId, pageable);

        return flashcardDeckRepository
            .findByUserIdAndTitleContainingIgnoreCaseOrderByPinnedDescCreatedAtDescIdDesc(userId, search, pageable);
    }

    private FlashcardListResponse toFlashcardListResponse(List<SingleDeckResponse> decks, Page<FlashcardDeck> page) {
        return new FlashcardListResponse(
            decks,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext()
        );
    }

    /**
     * Returns a single deck of flashcards.
     * @param publicId the public id of the flashcard deck.
     * @param userId the id of the currently authenticated user.
     * @return a deck id and a list of flashcards in the deck.
     * @throws DeckNotFound if a deck with a given public deck id doesn't exist for this user.
     */
    public SingleDeckResponse getSingleFlashcardDeck(String publicId, Long userId) {
        FlashcardDeck deck = flashcardDeckRepository
            .findByPublicIdAndUserId(publicId, userId)
            .orElseThrow(() -> new DeckNotFound("Flashcard deck not found: " + publicId));

        List<Flashcard> flashcards = flashcardRepository.findByDeckIdOrderByPositionAsc(deck.getId());

        List<FlashcardWithIdResponse> flashcardList = flashcards
                .stream()
                .map(f -> new FlashcardWithIdResponse(f.getPublicId(), f.getQuestion(), f.getAnswer()))
                .toList();

        return new SingleDeckResponse(
            publicId,
            deck.getTitle(),
            flashcardList,
            groupPublicId(deck.getGroupId()),
            deck.isPinned()
        );
    }

    /**
     * Updates the title of a deck owned by the given user.
     *
     * @param deckId the public id of the deck.
     * @param userId the id of the currently authenticated user.
     * @param title the new deck title, or null to leave it unchanged.
     * @param pinned the new pin state, or null to leave it unchanged.
     * @return the updated deck with its cards in position order.
     * @throws DeckNotFound if the deck doesn't exist for this user.
     */
    @Transactional
    public SingleDeckResponse updateDeck(String deckId, Long userId, String title, Boolean pinned) {
        FlashcardDeck deck = flashcardDeckRepository
            .findByPublicIdAndUserId(deckId, userId)
            .orElseThrow(() -> new DeckNotFound("Flashcard deck not found: " + deckId));

        if (title != null)
            deck.updateTitle(title);

        if (pinned != null)
            deck.updatePinned(pinned);

        flashcardDeckRepository.save(deck);

        List<Flashcard> flashcards = flashcardRepository.findByDeckIdOrderByPositionAsc(deck.getId());

        List<FlashcardWithIdResponse> flashcardList = flashcards
            .stream()
            .map(f -> new FlashcardWithIdResponse(f.getPublicId(), f.getQuestion(), f.getAnswer()))
            .toList();

        return new SingleDeckResponse(
            deck.getPublicId(),
            deck.getTitle(),
            flashcardList,
            groupPublicId(deck.getGroupId()),
            deck.isPinned()
        );
    }

    /**
     * Resolves the public id of the study group a deck belongs to.
     *
     * @param groupId the internal group id held by the deck, or null when it is ungrouped.
     * @return the group's public id, or null when the deck is not in a group.
     */
    private String groupPublicId(Long groupId) {
        if (groupId == null)
            return null;

        return studyGroupRepository.findPublicIdById(groupId).orElse(null);
    }

    /**
     * Deletes a deck and all its flashcards from the DB.
     * @param publicId the public id of the deck.
     * @param userId the id of the currently authenticated user.
     * @throws DeckNotFound if the deck doesn't exist.
     */
    @Transactional
    public void deleteDeck(String publicId, Long userId) {
        long isDeleted = flashcardDeckRepository.deleteByPublicIdAndUserId(publicId, userId);

        if (isDeleted == 0)
            throw new DeckNotFound("Flashcard deck not found: " + publicId);
    }

    /**
     * Persists a new flashcard in a deck owned by the given user.
     *
     * @param deckId the public id of the deck to add the flashcard to.
     * @param userId the id of the currently authenticated user.
     * @param req the flashcard question and answer to save.
     * @return the newly created flashcard.
     * @throws DeckNotFound if the deck doesn't exist for this user.
     */
    @Transactional
    public AddFlashcardResponse addFlashcard(String deckId, Long userId, AddFlashcardRequest req) {
        FlashcardDeck deck = flashcardDeckRepository
            .findByPublicIdAndUserId(deckId, userId)
            .orElseThrow(() -> new DeckNotFound("Deck not found: " + deckId));

        Integer maxPosition = flashcardRepository.findMaxPositionByDeckId(deck.getId()).orElse(-1);
        Integer newPosition = maxPosition + 1;

        Flashcard newFlashcard = new Flashcard(deck.getId(), req.question(), req.answer(), newPosition);

        Flashcard savedCard = flashcardRepository.save(newFlashcard);

        flashcardDeckRepository.updateUpdatedAtById(deck.getId());

        return new AddFlashcardResponse(
            savedCard.getPublicId(),
            savedCard.getQuestion(),
            savedCard.getAnswer(),
            savedCard.getCreatedAt()
        );
    }

    /**
     * Returns the decks owned by the user that are due for review today.
     *
     * <p>A deck is due when its next review date is today or earlier, counted in the user's own
     * time zone. A deck that has never been
     * reviewed has no next review date and is not queued, so a new deck only joins the queue once
     * it has been played and rated. Decks are ordered oldest due date first, then by insertion
     * order so the queue is stable between requests.</p>
     *
     * @param userId the id of the currently authenticated user.
     * @return the due decks with the metadata needed to queue and select one.
     */
    public ReviewQueueResponse getReviewQueue(Long userId) {
        List<FlashcardDeck> decks = flashcardDeckRepository
            .findByUserIdAndNextReviewDateLessThanEqualOrderByNextReviewDateAscIdAsc(
                userId,
                userTimeZoneService.today(userId)
            );

        if (decks.isEmpty())
            return new ReviewQueueResponse(List.of());

        List<Long> deckIds = decks.stream().map(FlashcardDeck::getId).toList();

        Map<Long, Long> cardCounts = flashcardRepository.findByDeckIdInOrderByDeckIdAscPositionAsc(deckIds)
            .stream()
            .collect(Collectors.groupingBy(Flashcard::getDeckId, Collectors.counting()));

        List<ReviewQueueDeckResponse> dueDecks = new ArrayList<>();

        for (FlashcardDeck deck : decks) {
            dueDecks.add(
                new ReviewQueueDeckResponse(
                    deck.getPublicId(),
                    deck.getTitle(),
                    cardCounts.getOrDefault(deck.getId(), 0L).intValue(),
                    deck.getNextReviewDate(),
                    deck.getIntervalDays(),
                    deck.getReviewCount(),
                    deck.getLastReviewedAt(),
                    deck.getLastRating()
                )
            );
        }

        return new ReviewQueueResponse(dueDecks);
    }

    /**
     * Records a review of a deck owned by the given user and reschedules it.
     *
     * <p>The schedule update, the review history row, and the increment of the user's lifetime
     * cards-reviewed counter all happen in one transaction, so a failed ownership check or an
     * empty deck changes nothing.</p>
     *
     * <p>The deck is loaded with a pessimistic write lock, so concurrent reviews of the same deck
     * run one after the other and the second review schedules from the state the first one saved.
     * Ordinary deck retrieval stays unlocked.</p>
     *
     * @param deckId the public id of the reviewed deck.
     * @param userId the id of the currently authenticated user.
     * @param rating how well the user recalled the deck.
     * @param durationSeconds how long the session took, or null when the client did not report it.
     * @return the applied rating, new schedule, cards reviewed, and the user's lifetime count.
     * @throws DeckNotFound if the deck doesn't exist for this user.
     * @throws EmptyDeckException if the deck has no flashcards.
     */
    @Transactional
    public ReviewDeckResponse reviewDeck(
        String deckId,
        Long userId,
        ReviewRating rating,
        Integer durationSeconds
    ) {
        FlashcardDeck deck = flashcardDeckRepository
            .findByPublicIdAndUserIdForReview(deckId, userId)
            .orElseThrow(() -> new DeckNotFound("Deck not found: " + deckId));

        int cardsReviewed = flashcardRepository.countByDeckId(deck.getId());

        if (cardsReviewed == 0)
            throw new EmptyDeckException("Deck has no flashcards to review: " + deckId);

        int previousIntervalDays = deck.getIntervalDays();
        BigDecimal previousEaseFactor = deck.getEaseFactor();
        int newIntervalDays = calculateIntervalDays(rating, previousIntervalDays, previousEaseFactor);
        BigDecimal newEaseFactor = calculateEaseFactor(rating, previousEaseFactor);
        // The instant is stored as UTC; only the due date is counted in the user's calendar.
        LocalDateTime reviewedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate nextReviewDate = userTimeZoneService.today(userId).plusDays(newIntervalDays);

        deck.applyReview(
            rating,
            newIntervalDays,
            newEaseFactor,
            nextReviewDate,
            reviewedAt
        );
        flashcardDeckRepository.save(deck);

        flashcardDeckReviewRepository.save(
            new FlashcardDeckReview(
                deck.getId(),
                rating,
                cardsReviewed,
                cardsReviewed,
                previousIntervalDays,
                newIntervalDays,
                previousEaseFactor,
                newEaseFactor,
                reviewedAt,
                durationSeconds
            )
        );

        userRepository.incrementTotalFlashcardsReviewed(userId, cardsReviewed);

        return new ReviewDeckResponse(
            deck.getPublicId(),
            rating,
            newIntervalDays,
            deck.getNextReviewDate(),
            cardsReviewed,
            userRepository.findTotalFlashcardsReviewedById(userId)
        );
    }

    /**
     * Calculates the new interval in whole days for a rated review.
     *
     * @param rating how well the user recalled the deck.
     * @param currentIntervalDays the deck's interval before this review.
     * @param easeFactor the deck's ease factor before this review.
     * @return the new interval in whole days.
     */
    private int calculateIntervalDays(ReviewRating rating, int currentIntervalDays, BigDecimal easeFactor) {
        double ease = easeFactor.doubleValue();

        return switch (rating) {
            case AGAIN -> 0;
            case HARD -> Math.max(1, (int) Math.round(currentIntervalDays * HARD_INTERVAL_MULTIPLIER));
            case GOOD -> calculateGoodIntervalDays(currentIntervalDays, ease);
            case EASY -> Math.max(
                EASY_MINIMUM_INTERVAL_DAYS,
                (int) Math.round(Math.max(1, currentIntervalDays) * ease * EASY_INTERVAL_MULTIPLIER)
            );
        };
    }

    /**
     * Calculates the new interval for a {@code GOOD} review.
     *
     * @param currentIntervalDays the deck's interval before this review.
     * @param ease the deck's ease factor before this review.
     * @return one day for a deck that has never been reviewed, six days for a one-day interval,
     *     and the current interval scaled by the ease factor otherwise.
     */
    private int calculateGoodIntervalDays(int currentIntervalDays, double ease) {
        if (currentIntervalDays == 0)
            return 1;

        if (currentIntervalDays == 1)
            return GOOD_SECOND_INTERVAL_DAYS;

        return (int) Math.round(currentIntervalDays * ease);
    }

    /**
     * Calculates the new ease factor for a rated review, floored at the minimum ease factor.
     *
     * @param rating how well the user recalled the deck.
     * @param currentEaseFactor the deck's ease factor before this review.
     * @return the adjusted ease factor, never below 1.30.
     */
    private BigDecimal calculateEaseFactor(ReviewRating rating, BigDecimal currentEaseFactor) {
        BigDecimal adjustment = switch (rating) {
            case AGAIN -> new BigDecimal("-0.20");
            case HARD -> new BigDecimal("-0.15");
            case GOOD -> BigDecimal.ZERO;
            case EASY -> new BigDecimal("0.15");
        };

        return currentEaseFactor.add(adjustment).max(MINIMUM_EASE_FACTOR);
    }

    /**
     * Updates the supplied fields of a flashcard in a deck owned by the given user.
     *
     * <p>The parent deck's modified timestamp is advanced, matching manual card creation
     * and deletion.</p>
     *
     * @param deckId the public id of the flashcard deck.
     * @param userId the id of the currently authenticated user.
     * @param cardId the public id of the flashcard to update.
     * @param question the new question, or null to leave it unchanged.
     * @param answer the new answer, or null to leave it unchanged.
     * @return the updated flashcard.
     * @throws DeckNotFound if the deck doesn't exist for this user.
     * @throws FlashcardNotFound if the flashcard doesn't exist in the deck.
     */
    @Transactional
    public AddFlashcardResponse updateFlashcard(
        String deckId,
        Long userId,
        String cardId,
        String question,
        String answer
    ) {
        FlashcardDeck deck = flashcardDeckRepository
            .findByPublicIdAndUserId(deckId, userId)
            .orElseThrow(() -> new DeckNotFound("Deck not found: " + deckId));

        Flashcard card = flashcardRepository
            .findByPublicIdAndDeckId(cardId, deck.getId())
            .orElseThrow(() -> new FlashcardNotFound("Flashcard not found: " + cardId));

        if (question != null)
            card.updateQuestion(question);

        if (answer != null)
            card.updateAnswer(answer);

        flashcardRepository.save(card);

        flashcardDeckRepository.updateUpdatedAtById(deck.getId());

        return new AddFlashcardResponse(
            card.getPublicId(),
            card.getQuestion(),
            card.getAnswer(),
            card.getCreatedAt()
        );
    }

    /**
     * Deletes a flashcard from a deck owned by the given user.
     *
     * @param userId the id of the currently authenticated user.
     * @param deckId the public id of the flashcard deck.
     * @param flashcardId the public id of the flashcard to delete.
     * @throws DeckNotFound if the deck doesn't exist for this user.
     * @throws FlashcardNotFound if the flashcard doesn't exist in the deck.
     */
    @Transactional
    public void deleteFlashcard(Long userId, String deckId, String flashcardId) {
        FlashcardDeck deck = flashcardDeckRepository
            .findByPublicIdAndUserId(deckId, userId)
            .orElseThrow(() -> new DeckNotFound("Deck not found: " + deckId));

        long isDeleted = flashcardRepository.deleteByPublicIdAndDeckId(flashcardId, deck.getId());

        if (isDeleted == 0)
            throw new FlashcardNotFound("Flashcard not found: " + flashcardId);

        flashcardDeckRepository.updateUpdatedAtById(deck.getId());
    }

}
