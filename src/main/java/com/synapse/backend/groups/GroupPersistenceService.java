package com.synapse.backend.groups;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.synapse.backend.flashcards.exceptions.DeckNotFound;
import com.synapse.backend.flashcards.repositories.FlashcardDeckRepository;
import com.synapse.backend.groups.dto.GroupContentResponse;
import com.synapse.backend.groups.dto.GroupDetailResponse;
import com.synapse.backend.groups.dto.GroupListItemResponse;
import com.synapse.backend.groups.dto.GroupListResponse;
import com.synapse.backend.groups.dto.GroupResponse;
import com.synapse.backend.groups.entities.StudyGroup;
import com.synapse.backend.groups.exceptions.GroupNotFound;
import com.synapse.backend.groups.repositories.StudyGroupRepository;
import com.synapse.backend.notes.exceptions.NoteNotFoundException;
import com.synapse.backend.notes.repositories.NoteRepository;
import com.synapse.backend.quiz.exceptions.QuizNotFound;
import com.synapse.backend.quiz.repositories.QuizRepository;

import jakarta.transaction.Transactional;

@Service
public class GroupPersistenceService {
    private final StudyGroupRepository studyGroupRepository;
    private final NoteRepository noteRepository;
    private final FlashcardDeckRepository flashcardDeckRepository;
    private final QuizRepository quizRepository;

    public GroupPersistenceService(
        StudyGroupRepository studyGroupRepository,
        NoteRepository noteRepository,
        FlashcardDeckRepository flashcardDeckRepository,
        QuizRepository quizRepository
    ) {
        this.studyGroupRepository = studyGroupRepository;
        this.noteRepository = noteRepository;
        this.flashcardDeckRepository = flashcardDeckRepository;
        this.quizRepository = quizRepository;
    }

    /**
     * Persists a new empty group owned by the given user.
     *
     * @param userId the id of the currently authenticated user.
     * @param name the validated group name.
     * @param description the validated group description, or null.
     * @return the newly created group.
     */
    @Transactional
    public GroupResponse createGroup(Long userId, String name, String description) {
        StudyGroup group = studyGroupRepository.save(new StudyGroup(userId, name, description));

        return toGroupResponse(group);
    }

    /**
     * Returns a page of groups owned by the user with their content counts, optionally filtered by name.
     *
     * @param userId the id of the currently authenticated user.
     * @param query an optional case-insensitive partial name search, or null/blank for no search.
     * @param pageable the page to return.
     * @return the requested page of the user's groups, ordered newest first.
     */
    public GroupListResponse getAllGroups(Long userId, String query, Pageable pageable) {
        Page<StudyGroup> groups = findGroupsPage(userId, query, pageable);

        return new GroupListResponse(
            groups
                .stream()
                .map(g -> new GroupListItemResponse(
                    g.getPublicId(),
                    g.getName(),
                    g.getDescription(),
                    noteRepository.countByGroupId(g.getId()),
                    flashcardDeckRepository.countByGroupId(g.getId()),
                    quizRepository.countByGroupId(g.getId()),
                    g.getCreatedAt()
                ))
                .toList(),
            groups.getNumber(),
            groups.getSize(),
            groups.getTotalElements(),
            groups.getTotalPages(),
            groups.hasNext()
        );
    }

    private Page<StudyGroup> findGroupsPage(Long userId, String query, Pageable pageable) {
        String search = query == null ? "" : query.trim();

        if (search.isEmpty())
            return studyGroupRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);

        return studyGroupRepository
            .findByUserIdAndNameContainingIgnoreCaseOrderByCreatedAtDescIdDesc(userId, search, pageable);
    }

    /**
     * Returns a single group owned by the user with the content it holds.
     *
     * @param groupId the public id of the group.
     * @param userId the id of the currently authenticated user.
     * @return the group and lightweight lists of its notes, decks, and quizzes.
     * @throws GroupNotFound if the group doesn't exist for this user.
     */
    public GroupDetailResponse getGroup(String groupId, Long userId) {
        StudyGroup group = findOwnedGroup(groupId, userId);

        List<GroupContentResponse> notes = noteRepository
            .findByGroupIdOrderByPinnedDescCreatedAtDescIdDesc(group.getId())
            .stream()
            .map(n -> new GroupContentResponse(n.getPublicId(), n.getTitle(), n.getCreatedAt(), n.isPinned()))
            .toList();

        List<GroupContentResponse> decks = flashcardDeckRepository
            .findByGroupIdOrderByPinnedDescCreatedAtDescIdDesc(group.getId())
            .stream()
            .map(d -> new GroupContentResponse(d.getPublicId(), d.getTitle(), d.getCreatedAt(), d.isPinned()))
            .toList();

        List<GroupContentResponse> quizzes = quizRepository
            .findByGroupIdOrderByPinnedDescCreatedAtDescIdDesc(group.getId())
            .stream()
            .map(q -> new GroupContentResponse(q.getPublicId(), q.getTitle(), q.getCreatedAt(), q.isPinned()))
            .toList();

        return new GroupDetailResponse(
            group.getPublicId(),
            group.getName(),
            group.getDescription(),
            notes,
            decks,
            quizzes,
            group.getCreatedAt()
        );
    }

    /**
     * Updates the supplied fields of a group owned by the user.
     *
     * @param groupId the public id of the group.
     * @param userId the id of the currently authenticated user.
     * @param name the new name, or null to leave it unchanged.
     * @param description the new description, or null to leave it unchanged.
     * @return the updated group.
     * @throws GroupNotFound if the group doesn't exist for this user.
     */
    @Transactional
    public GroupResponse updateGroup(String groupId, Long userId, String name, String description) {
        StudyGroup group = findOwnedGroup(groupId, userId);

        if (name != null)
            group.updateName(name);

        if (description != null)
            group.updateDescription(description.isBlank() ? null : description);

        studyGroupRepository.save(group);

        return toGroupResponse(group);
    }

    /**
     * Deletes a group owned by the user.
     *
     * <p>The group's notes, decks, and quizzes are kept and become ungrouped, because every
     * {@code group_id} foreign key is {@code ON DELETE SET NULL}.</p>
     *
     * @param groupId the public id of the group.
     * @param userId the id of the currently authenticated user.
     * @throws GroupNotFound if the group doesn't exist for this user.
     */
    @Transactional
    public void deleteGroup(String groupId, Long userId) {
        long isDeleted = studyGroupRepository.deleteByPublicIdAndUserId(groupId, userId);

        if (isDeleted == 0)
            throw new GroupNotFound("Group not found: " + groupId);
    }

    /**
     * Adds a note owned by the user to a group owned by the same user.
     *
     * <p>A note that is already in another group is moved rather than duplicated.</p>
     *
     * @param groupId the public id of the group.
     * @param noteId the public id of the note.
     * @param userId the id of the currently authenticated user.
     * @throws GroupNotFound if the group doesn't exist for this user.
     * @throws NoteNotFoundException if the note doesn't exist for this user.
     */
    @Transactional
    public void addNote(String groupId, String noteId, Long userId) {
        StudyGroup group = findOwnedGroup(groupId, userId);

        long isUpdated = noteRepository.updateGroupIdByPublicIdAndUserId(noteId, userId, group.getId());

        if (isUpdated == 0)
            throw new NoteNotFoundException("Note could not be found: " + noteId);
    }

    /**
     * Removes a note from a group owned by the user, keeping the note itself.
     *
     * @param groupId the public id of the group.
     * @param noteId the public id of the note.
     * @param userId the id of the currently authenticated user.
     * @throws GroupNotFound if the group doesn't exist for this user.
     * @throws NoteNotFoundException if the note isn't in that group for this user.
     */
    @Transactional
    public void removeNote(String groupId, String noteId, Long userId) {
        StudyGroup group = findOwnedGroup(groupId, userId);

        long isUpdated = noteRepository.clearGroupIdByPublicIdAndUserId(noteId, userId, group.getId());

        if (isUpdated == 0)
            throw new NoteNotFoundException("Note could not be found: " + noteId);
    }

    /**
     * Adds a flashcard deck owned by the user to a group owned by the same user.
     *
     * <p>A deck that is already in another group is moved rather than duplicated.</p>
     *
     * @param groupId the public id of the group.
     * @param deckId the public id of the deck.
     * @param userId the id of the currently authenticated user.
     * @throws GroupNotFound if the group doesn't exist for this user.
     * @throws DeckNotFound if the deck doesn't exist for this user.
     */
    @Transactional
    public void addDeck(String groupId, String deckId, Long userId) {
        StudyGroup group = findOwnedGroup(groupId, userId);

        long isUpdated = flashcardDeckRepository.updateGroupIdByPublicIdAndUserId(deckId, userId, group.getId());

        if (isUpdated == 0)
            throw new DeckNotFound("Deck not found: " + deckId);
    }

    /**
     * Removes a flashcard deck from a group owned by the user, keeping the deck itself.
     *
     * @param groupId the public id of the group.
     * @param deckId the public id of the deck.
     * @param userId the id of the currently authenticated user.
     * @throws GroupNotFound if the group doesn't exist for this user.
     * @throws DeckNotFound if the deck isn't in that group for this user.
     */
    @Transactional
    public void removeDeck(String groupId, String deckId, Long userId) {
        StudyGroup group = findOwnedGroup(groupId, userId);

        long isUpdated = flashcardDeckRepository.clearGroupIdByPublicIdAndUserId(deckId, userId, group.getId());

        if (isUpdated == 0)
            throw new DeckNotFound("Deck not found: " + deckId);
    }

    /**
     * Adds a quiz owned by the user to a group owned by the same user.
     *
     * <p>A quiz that is already in another group is moved rather than duplicated.</p>
     *
     * @param groupId the public id of the group.
     * @param quizId the public id of the quiz.
     * @param userId the id of the currently authenticated user.
     * @throws GroupNotFound if the group doesn't exist for this user.
     * @throws QuizNotFound if the quiz doesn't exist for this user.
     */
    @Transactional
    public void addQuiz(String groupId, String quizId, Long userId) {
        StudyGroup group = findOwnedGroup(groupId, userId);

        long isUpdated = quizRepository.updateGroupIdByPublicIdAndUserId(quizId, userId, group.getId());

        if (isUpdated == 0)
            throw new QuizNotFound("Quiz not found: " + quizId);
    }

    /**
     * Removes a quiz from a group owned by the user, keeping the quiz itself.
     *
     * @param groupId the public id of the group.
     * @param quizId the public id of the quiz.
     * @param userId the id of the currently authenticated user.
     * @throws GroupNotFound if the group doesn't exist for this user.
     * @throws QuizNotFound if the quiz isn't in that group for this user.
     */
    @Transactional
    public void removeQuiz(String groupId, String quizId, Long userId) {
        StudyGroup group = findOwnedGroup(groupId, userId);

        long isUpdated = quizRepository.clearGroupIdByPublicIdAndUserId(quizId, userId, group.getId());

        if (isUpdated == 0)
            throw new QuizNotFound("Quiz not found: " + quizId);
    }

    /**
     * Resolves a group by its public id and owner.
     *
     * @param groupId the public id of the group.
     * @param userId the id of the currently authenticated user.
     * @return the user-owned group.
     * @throws GroupNotFound if the group doesn't exist for this user.
     */
    private StudyGroup findOwnedGroup(String groupId, Long userId) {
        return studyGroupRepository
            .findByPublicIdAndUserId(groupId, userId)
            .orElseThrow(() -> new GroupNotFound("Group not found: " + groupId));
    }

    private GroupResponse toGroupResponse(StudyGroup group) {
        return new GroupResponse(
            group.getPublicId(),
            group.getName(),
            group.getDescription(),
            group.getCreatedAt()
        );
    }

}
