package com.synapse.backend.notes;

import java.util.List;

import org.springframework.stereotype.Service;

import com.synapse.backend.notes.dto.ConceptSummary;
import com.synapse.backend.notes.dto.NoteSummaryResponse;
import com.synapse.backend.notes.entities.Note;
import com.synapse.backend.notes.entities.NoteConcept;
import com.synapse.backend.notes.entities.NoteImportantTerm;
import com.synapse.backend.notes.entities.NoteKeypoint;
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

    public NotesPersistenceService(
        NoteRepository noteRepository,
        NoteKeypointRepository noteKeypointRepository,
        NoteConceptRepository noteConceptRepository,
        NoteImportantTermRepository noteImportantTermRepository
    ) {
        this.noteRepository = noteRepository;
        this.noteKeypointRepository = noteKeypointRepository;
        this.noteConceptRepository = noteConceptRepository;
        this.noteImportantTermRepository = noteImportantTermRepository;
    }

    /**
     * Saves generated note summary to DB.
     *
     * @param summary summary of notes.
     * @param userId the ID of the user.
     */
    @Transactional
    public void saveNoteSummary(NoteSummaryResponse summary, Long userId) {
        Note note = new Note(userId, summary.title(), summary.overview());

        Note newNote = noteRepository.save(note);

        Long noteId = newNote.getId();
        saveNoteKeypoints(summary.keypoints(), noteId);
        saveNoteConcepts(summary.concepts(), noteId);
        saveNoteImportantTerms(summary.importantTerms(), noteId);
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

}
