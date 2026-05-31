package com.synapse.backend.quiz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.synapse.backend.quiz.dto.AnswerResponse;
import com.synapse.backend.quiz.dto.QuestionResponse;
import com.synapse.backend.quiz.dto.QuizResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedAnswerResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedQuestionResponse;
import com.synapse.backend.quiz.dto.generated.GeneratedQuizResponse;
import com.synapse.backend.quiz.dto.list.ListQuizResponse;
import com.synapse.backend.quiz.dto.list.QuestionPreviewResponse;
import com.synapse.backend.quiz.dto.list.QuizListItemResponse;
import com.synapse.backend.quiz.entities.Quiz;
import com.synapse.backend.quiz.entities.QuizAnswer;
import com.synapse.backend.quiz.entities.QuizQuestion;
import com.synapse.backend.quiz.enums.QuestionType;
import com.synapse.backend.quiz.enums.QuizSourceType;
import com.synapse.backend.quiz.repositories.QuizAnswerRepository;
import com.synapse.backend.quiz.repositories.QuizQuestionRepository;
import com.synapse.backend.quiz.repositories.QuizRepository;

import jakarta.transaction.Transactional;

@Service
public class QuizPersistenceService {
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizAnswerRepository answerRepository;

    public QuizPersistenceService(
        QuizRepository quizRepository,
        QuizQuestionRepository questionRepository,
        QuizAnswerRepository answerRepository
    ) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
    }

    /**
     * Saves a generated note-based quiz, including all questions and answers.
     *
     * @param generatedQuiz generated quiz data returned by the LLM.
     * @param userId the id of the authenticated user who owns the quiz.
     * @param noteId the internal id of the source note.
     * @return the saved quiz response with public ids and timestamps.
     */
    @Transactional
    public QuizResponse saveQuizFromNote(GeneratedQuizResponse generatedQuiz, Long userId, Long noteId) {
        Quiz quiz = new Quiz(generatedQuiz.title(), generatedQuiz.description(), userId, noteId, QuizSourceType.NOTE);
        Quiz newQuiz = quizRepository.save(quiz);

        List<GeneratedQuestionResponse> generatedQuestions = generatedQuiz.questions();
        List<QuestionResponse> questions = new ArrayList<>();

        for (int i = 0; i < generatedQuestions.size(); i++) {
            GeneratedQuestionResponse question = generatedQuestions.get(i);
            QuizQuestion quizQuestion = saveQuestion(question, newQuiz.getId(), i);

            List<QuizAnswer> answers = saveAnswers(question.answers(), quizQuestion.getId());

            List<AnswerResponse> answerResponses = answers.stream()
                .map(a -> new AnswerResponse(
                    a.getPublicId(),
                    a.getAnswerText(),
                    a.isCorrect(),
                    a.getCreatedAt()
                ))
                .toList();

            questions.add(
                new QuestionResponse(
                    quizQuestion.getPublicId(),
                    quizQuestion.getQuestionText(),
                    quizQuestion.getQuestionType(),
                    answerResponses,
                    quizQuestion.getCreatedAt()
                )
            );
        }

        return new QuizResponse(
            newQuiz.getPublicId(),
            newQuiz.getTitle(),
            newQuiz.getDescription(),
            questions,
            newQuiz.getCreatedAt()
        );
    }

    /**
     * Persists a generated question for a quiz at the given position.
     *
     * @param generatedQuestion generated question data.
     * @param quizId internal id of the parent quiz.
     * @param position zero-based question position within the quiz.
     * @return the saved question entity.
     */
    private QuizQuestion saveQuestion(GeneratedQuestionResponse generatedQuestion, Long quizId, int position) {
        String questionText = generatedQuestion.questionText();
        QuestionType questionType = generatedQuestion.questionType();
        QuizQuestion question = new QuizQuestion(quizId, questionText, questionType, position);

        return questionRepository.save(question);
    }

    /**
     * Persists generated answers for a question, preserving their response order as position.
     *
     * @param generatedAnswers generated answer options.
     * @param questionId internal id of the parent question.
     * @return the saved answer entities.
     */
    private List<QuizAnswer> saveAnswers(List<GeneratedAnswerResponse> generatedAnswers, Long questionId) {
        List<QuizAnswer> answers = new ArrayList<>();

        for (int i = 0; i < generatedAnswers.size(); i++) {
            String answerText = generatedAnswers.get(i).answerText();
            boolean isCorrect = generatedAnswers.get(i).correct();

            answers.add(new QuizAnswer(questionId, answerText, isCorrect, i));
        }

        return answerRepository.saveAll(answers);
    }

    /**
     * Returns a list of all quizzes and their questions owned by a user.
     *
     * @param userId the user id of the currently authenticated user.
     * @return a full list of quizzes and their questions.
     */
    public ListQuizResponse getAllQuizzes(Long userId) {
        List<Quiz> quizzes = quizRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Long> quizIds = quizzes.stream().map(Quiz::getId).toList();

        if (quizIds.isEmpty())
            return new ListQuizResponse(List.of());

        Map<Long, List<QuizQuestion>> quizQuestions = questionRepository
            .findByQuizIdInOrderByQuizIdAscPositionAsc(quizIds)
            .stream()
            .collect(Collectors.groupingBy(QuizQuestion::getQuizId));

        List<QuizListItemResponse> quizResponses = new ArrayList<>();

        for (Quiz quiz : quizzes) {
            List<QuestionPreviewResponse> questionResponses = quizQuestions
                .getOrDefault(quiz.getId(), List.of())
                .stream()
                .map(q -> new QuestionPreviewResponse(q.getPublicId(), q.getQuestionText(), q.getCreatedAt()))
                .toList();

            quizResponses.add(
                new QuizListItemResponse(
                    quiz.getPublicId(),
                    quiz.getTitle(),
                    quiz.getDescription(),
                    questionResponses,
                    quiz.getCreatedAt()
                )
            );
        }

        return new ListQuizResponse(quizResponses);
    }

}
