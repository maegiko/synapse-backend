package com.synapse.backend.notes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.synapse.backend.groups.repositories.StudyGroupRepository;
import com.synapse.backend.notes.dto.ConceptSummary;
import com.synapse.backend.notes.dto.NoteForGeneration;
import com.synapse.backend.notes.dto.NoteSummaryResponse;
import com.synapse.backend.notes.entities.Note;
import com.synapse.backend.notes.entities.NoteConcept;
import com.synapse.backend.notes.entities.NoteImportantTerm;
import com.synapse.backend.notes.entities.NoteKeypoint;
import com.synapse.backend.notes.exceptions.NoteNotFoundException;
import com.synapse.backend.notes.repositories.NoteConceptRepository;
import com.synapse.backend.notes.repositories.NoteImportantTermRepository;
import com.synapse.backend.notes.repositories.NoteKeypointRepository;
import com.synapse.backend.notes.repositories.NoteRepository;

import jakarta.transaction.Transactional;

@Service
public class NotesPersistenceService {
    private final NoteRepository noteRepository;
    private final NoteKeypointRepository noteKeypointRepository;
    private final NoteConceptRepository noteConceptRepository;
    private final NoteImportantTermRepository noteImportantTermRepository;
    private final StudyGroupRepository studyGroupRepository;

    public NotesPersistenceService(
        NoteRepository noteRepository,
        NoteKeypointRepository noteKeypointRepository,
        NoteConceptRepository noteConceptRepository,
        NoteImportantTermRepository noteImportantTermRepository,
        StudyGroupRepository studyGroupRepository
    ) {
        this.noteRepository = noteRepository;
        this.noteKeypointRepository = noteKeypointRepository;
        this.noteConceptRepository = noteConceptRepository;
        this.noteImportantTermRepository = noteImportantTermRepository;
        this.studyGroupRepository = studyGroupRepository;
    }

    /**
     * Saves generated note summary to DB.
     *
     * @param summary summary of notes.
     * @param userId the ID of the user.
     * @return saved summary with the generated public note id.
     */
    @Transactional
    public NoteSummaryResponse saveNoteSummary(NoteSummaryResponse summary, Long userId) {
        Note note = new Note(userId, summary.title(), summary.overview());

        Note newNote = noteRepository.save(note);

        Long noteId = newNote.getId();
        saveNoteKeypoints(summary.keypoints(), noteId);
        saveNoteConcepts(summary.concepts(), noteId);
        saveNoteImportantTerms(summary.importantTerms(), noteId);

        return new NoteSummaryResponse(
            newNote.getPublicId(),
            summary.title(),
            summary.overview(),
            summary.keypoints(),
            summary.concepts(),
            summary.importantTerms(),
            null
        );
    }

    private void saveNoteKeypoints(List<String> keypoints, Long noteId) {
        if (keypoints == null)
            return;

        for (int i = 0; i < keypoints.size(); i++) {
            String keypoint = keypoints.get(i);
            NoteKeypoint newKeypoint = new NoteKeypoint(noteId, i, keypoint);

            noteKeypointRepository.save(newKeypoint);
        }
    }

    private void saveNoteConcepts(List<ConceptSummary> concepts, Long noteId) {
        if (concepts == null)
            return;

        for (int i = 0; i < concepts.size(); i++) {
            ConceptSummary concept = concepts.get(i);
            NoteConcept newConcept = new NoteConcept(noteId, i, concept.name(), concept.explanation());

            noteConceptRepository.save(newConcept);
        }
    }

    private void saveNoteImportantTerms(List<String> importantTerms, Long noteId) {
        if (importantTerms == null)
            return;

        for (int i = 0; i < importantTerms.size(); i++) {
            String term = importantTerms.get(i);
            NoteImportantTerm newTerm = new NoteImportantTerm(noteId, i, term);

            noteImportantTermRepository.save(newTerm);
        }
    }

    /**
     * Returns all saved note summaries for user.
     *
     * @param userId the ID of the user.
     * @return all saved note summaries for the user.
     */
    public List<NoteSummaryResponse> getAllNoteSummaries(Long userId) {
        List<Note> notes = noteRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<Long> noteIds = notes.stream().map(Note::getId).toList();

        if (noteIds.isEmpty())
            return List.of();

        Map<Long, List<NoteKeypoint>> keypoints = getKeypointsByNoteId(noteIds);
        Map<Long, List<NoteConcept>> concepts = getConceptsByNoteId(noteIds);
        Map<Long, List<NoteImportantTerm>> importantTerms = getImportantTermsByNoteId(noteIds);

        return buildNoteSummaryResponses(notes, keypoints, concepts, importantTerms);
    }

    private Map<Long, List<NoteKeypoint>> getKeypointsByNoteId(List<Long> noteIds) {
        return noteKeypointRepository
            .findByNoteIdInOrderByNoteIdAscPositionAsc(noteIds)
            .stream()
            .collect(Collectors.groupingBy(NoteKeypoint::getNoteId));
    }

    private Map<Long, List<NoteConcept>> getConceptsByNoteId(List<Long> noteIds) {
        return noteConceptRepository
            .findByNoteIdInOrderByNoteIdAscPositionAsc(noteIds)
            .stream()
            .collect(Collectors.groupingBy(NoteConcept::getNoteId));
    }

    private Map<Long, List<NoteImportantTerm>> getImportantTermsByNoteId(List<Long> noteIds) {
        return noteImportantTermRepository
            .findByNoteIdInOrderByNoteIdAscPositionAsc(noteIds)
            .stream()
            .collect(Collectors.groupingBy(NoteImportantTerm::getNoteId));
    }

    private List<NoteSummaryResponse> buildNoteSummaryResponses(
        List<Note> notes,
        Map<Long, List<NoteKeypoint>> keypoints,
        Map<Long, List<NoteConcept>> concepts,
        Map<Long, List<NoteImportantTerm>> importantTerms
    ) {
        List<NoteSummaryResponse> allNotes = new ArrayList<>();

        for (Note note : notes) {
            Long noteId = note.getId();

            List<String> noteKeypoints = keypoints
                .getOrDefault(noteId, List.of())
                .stream()
                .map(NoteKeypoint::getKeypoint)
                .toList();

            List<ConceptSummary> noteConcepts = concepts
                .getOrDefault(noteId, List.of())
                .stream()
                .map(concept -> new ConceptSummary(concept.getName(), concept.getExplanation()))
                .toList();

            List<String> noteImportantTerms = importantTerms
                .getOrDefault(noteId, List.of())
                .stream()
                .map(NoteImportantTerm::getTerm)
                .toList();

            allNotes.add(new NoteSummaryResponse(
                note.getPublicId(),
                note.getTitle(),
                note.getOverview(),
                noteKeypoints,
                noteConcepts,
                noteImportantTerms,
                groupPublicId(note.getGroupId())
            ));
        }

        return allNotes;
    }

    /**
     * Returns a single note belonging to a user.
     *
     * @param noteId the note ID of the note to return.
     * @param userId the user ID of the user requesting the note.
     * @return the note belonging to the user of a given noteId.
     * @throws NoteNotFoundException if a note with noteId belonging to user with userId does not exist.
     */
    public NoteSummaryResponse getNoteSummary(String noteId, Long userId) {
        Note note = noteRepository
            .findByPublicIdAndUserId(noteId, userId)
            .orElseThrow(() -> new NoteNotFoundException("Requested note not found."));

        return buildSummaryResponse(note);
    }

    private NoteSummaryResponse buildSummaryResponse(Note note) {
        List<NoteConcept> concepts = noteConceptRepository.findByNoteIdOrderByPositionAsc(note.getId());
        List<NoteKeypoint> keypoints = noteKeypointRepository.findByNoteIdOrderByPositionAsc(note.getId());
        List<NoteImportantTerm> importantTerms = noteImportantTermRepository.findByNoteIdOrderByPositionAsc(note.getId());

        List<String> noteKeypoints = keypoints.stream().map(k -> k.getKeypoint()).toList();
        List<ConceptSummary> noteConcepts = concepts
            .stream()
            .map(c -> new ConceptSummary(c.getName(), c.getExplanation()))
            .toList();
        List<String> noteImportantTerms = importantTerms.stream().map(t -> t.getTerm()).toList();

        return new NoteSummaryResponse(
            note.getPublicId(),
            note.getTitle(),
            note.getOverview(),
            noteKeypoints,
            noteConcepts,
            noteImportantTerms,
            groupPublicId(note.getGroupId())
        );
    }

    /**
     * Resolves the public id of the study group a resource belongs to.
     *
     * @param groupId the internal group id held by the resource, or null when it is ungrouped.
     * @return the group's public id, or null when the resource is not in a group.
     */
    private String groupPublicId(Long groupId) {
        if (groupId == null)
            return null;

        return studyGroupRepository.findPublicIdById(groupId).orElse(null);
    }

    /**
     * Deletes a note and all its contents.
     * @param noteId the public id of the note.
     * @param userId the id of the currently authenticated user.
     * @throws NoteNotFound if the note doesn't exist.
     */
    @Transactional
    public void deleteNote(String noteId, Long userId) {
        long isDeleted = noteRepository.deleteByPublicIdAndUserId(noteId, userId);

        if (isDeleted == 0)
            throw new NoteNotFoundException("Note could not be found: " + noteId);
    }

    public NoteForGeneration getNoteForGeneration(String noteId, Long userId) {
        Note note = noteRepository
            .findByPublicIdAndUserId(noteId, userId)
            .orElseThrow(() -> new NoteNotFoundException("Requested note not found."));

        return new NoteForGeneration(note.getId(), buildSummaryResponse(note));
    }

}
