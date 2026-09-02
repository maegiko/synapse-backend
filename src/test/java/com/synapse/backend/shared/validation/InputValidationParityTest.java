package com.synapse.backend.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.synapse.backend.auth.dto.ChangePasswordRequest;
import com.synapse.backend.auth.dto.ForgotPasswordRequest;
import com.synapse.backend.auth.dto.LoginRequest;
import com.synapse.backend.auth.dto.RegisterRequest;
import com.synapse.backend.auth.dto.ResendVerificationRequest;
import com.synapse.backend.auth.dto.ResetPasswordRequest;
import com.synapse.backend.flashcards.dto.AddFlashcardRequest;
import com.synapse.backend.flashcards.dto.UpdateFlashcardRequest;
import com.synapse.backend.groups.dto.CreateGroupRequest;
import com.synapse.backend.groups.dto.UpdateGroupRequest;
import com.synapse.backend.quiz.dto.UpdateQuestionRequest;
import com.synapse.backend.quiz.dto.create.CreateQuestionAnswer;
import com.synapse.backend.quiz.dto.create.CreateQuestionRequest;
import com.synapse.backend.quiz.enums.QuestionType;
import com.synapse.backend.user.dto.ChangeEmailRequest;
import com.synapse.backend.user.dto.UpdateUserDetailsRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Holds the endpoint that creates a value and the endpoint that later edits it to the same
 * rules.
 *
 * <p>These pairs have drifted apart before: the name rule was enforced on registration and
 * not on the profile edit, and only one of the two flashcard endpoints trimmed its input.
 * The drift is invisible from either side on its own, which is what these tests are for.
 * Each one submits the same value to both DTOs and asserts they agree.</p>
 */
class InputValidationParityTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    @Test
    void bothFlashcardEndpointsBoundBothSidesOfACardIdentically() {
        String withinLimit = "q".repeat(ValidationLimits.FLASHCARD_TEXT_MAX);
        String overLimit = "q".repeat(ValidationLimits.FLASHCARD_TEXT_MAX + 1);

        assertAgree("question", q -> new AddFlashcardRequest(q, "answer"), q -> new UpdateFlashcardRequest(q, null));
        assertAgree("answer", a -> new AddFlashcardRequest("question", a), a -> new UpdateFlashcardRequest(null, a));

        assertThat(violations(new AddFlashcardRequest(withinLimit, withinLimit))).isEmpty();
        assertThat(violations(new UpdateFlashcardRequest(withinLimit, withinLimit))).isEmpty();
        assertThat(violations(new AddFlashcardRequest(overLimit, "answer"))).isNotEmpty();
        assertThat(violations(new UpdateFlashcardRequest(overLimit, null))).isNotEmpty();
    }

    @Test
    void bothQuestionEndpointsBoundTheQuestionTextIdentically() {
        assertAgree(
            "question",
            q -> new CreateQuestionRequest(q, QuestionType.BOOLEAN, List.of(answer("a", true))),
            q -> new UpdateQuestionRequest(q, null, null)
        );

        String overLimit = "q".repeat(ValidationLimits.QUESTION_TEXT_MAX + 1);

        assertThat(violations(new CreateQuestionRequest(overLimit, QuestionType.BOOLEAN, List.of()))).isNotEmpty();
        assertThat(violations(new UpdateQuestionRequest(overLimit, null, null))).isNotEmpty();
    }

    @Test
    void bothQuestionEndpointsBoundAnAnswerOptionIdentically() {
        String overLimit = "a".repeat(ValidationLimits.ANSWER_TEXT_MAX + 1);
        List<CreateQuestionAnswer> tooLong = List.of(answer(overLimit, true));

        assertThat(violations(new CreateQuestionRequest("Question?", QuestionType.BOOLEAN, tooLong))).isNotEmpty();
        assertThat(violations(new UpdateQuestionRequest(null, null, tooLong))).isNotEmpty();

        List<CreateQuestionAnswer> blank = List.of(answer("   ", true));

        assertThat(violations(new CreateQuestionRequest("Question?", QuestionType.BOOLEAN, blank))).isNotEmpty();
        assertThat(violations(new UpdateQuestionRequest(null, null, blank))).isNotEmpty();
    }

    @Test
    void bothGroupEndpointsBoundTheNameAndDescriptionIdentically() {
        assertAgree("name", n -> new CreateGroupRequest(n, null), n -> new UpdateGroupRequest(n, null));

        String overLimit = "d".repeat(ValidationLimits.DESCRIPTION_MAX + 1);

        assertThat(violations(new CreateGroupRequest("Systems", overLimit))).isNotEmpty();
        assertThat(violations(new UpdateGroupRequest(null, overLimit))).isNotEmpty();
    }

    @Test
    void registrationAndTheProfileEditBoundTheFullNameIdentically() {
        assertAgree(
            "fullName",
            n -> new RegisterRequest(n, "ada@example.com", "password123"),
            n -> new UpdateUserDetailsRequest(n, null)
        );
    }

    /**
     * Names that must keep working. Restricting a name field to {@code A-Z} is an easy
     * mistake to make and a hard one to notice from an ASCII test fixture, so the scripts are
     * named here explicitly.
     */
    @Test
    void bothNameEndpointsAcceptRealNames() {
        List<String> names = List.of(
            "Ada Lovelace",
            "Jean-Pierre",
            "O'Brien",
            "O\u2019Brien",
            "Anne-Marie O'Neill",
            "Jos\u00e9 M\u00fcller",
            "Ng\u00f4 \u0110\u00ecnh",
            "\u0412\u043b\u0430\u0434\u0438\u043c\u0438\u0440",
            "\u9673\u5927\u6587",
            "\u0623\u062d\u0645\u062f"
        );

        for (String name : names) {
            assertThat(violationsOn(new RegisterRequest(name, "ada@example.com", "password123"), "fullName"))
                .withFailMessage("registration rejected the real name \"%s\"", name)
                .isEmpty();
            assertThat(violationsOn(new UpdateUserDetailsRequest(name, null), "fullName"))
                .withFailMessage("the profile edit rejected the real name \"%s\"", name)
                .isEmpty();
        }
    }

    @Test
    void bothNameEndpointsRejectAnythingThatIsNotALetterOrANameJoiner() {
        List<String> notNames = List.of(
            "Kenneth 123",
            "Kenneth!",
            "<script>",
            "ada@example.com",
            "Ada \ud83d\ude00",
            "Ada_Lovelace",
            "Ada.Lovelace",
            "-Ada",
            "Ada-",
            "'",
            "--"
        );

        for (String notAName : notNames) {
            assertThat(violationsOn(new RegisterRequest(notAName, "ada@example.com", "password123"), "fullName"))
                .withFailMessage("registration accepted \"%s\" as a name", notAName)
                .isNotEmpty();
            assertThat(violationsOn(new UpdateUserDetailsRequest(notAName, null), "fullName"))
                .withFailMessage("the profile edit accepted \"%s\" as a name", notAName)
                .isNotEmpty();
        }
    }

    /**
     * Addresses that must keep working: subdomains, plus addressing, and the punctuation the
     * dot-atom local part allows.
     */
    @Test
    void everyEndpointThatTakesAnEmailAcceptsADeliverableAddress() {
        List<String> addresses = List.of(
            "ada@example.com",
            "ada.lovelace@example.com",
            "ada+tag@example.com",
            "ada_lovelace@sub.example.co.uk",
            "ada-lovelace@example.museum",
            "a@b.io",
            "ADA@EXAMPLE.COM"
        );

        for (String address : addresses) {
            for (Function<String, Object> request : emailRequests()) {
                assertThat(violationsOn(request.apply(address), "email"))
                    .withFailMessage("a deliverable address was rejected: %s", address)
                    .isEmpty();
            }
        }
    }

    /**
     * What the built-in {@code @Email} let through. Each of these is a shape no verification
     * mail can be delivered to, and each was accepted before {@link EmailAddress} replaced it.
     */
    @Test
    void everyEndpointThatTakesAnEmailRejectsAnUndeliverableAddress() {
        List<String> addresses = List.of(
            "asdfasdf@asdf",
            "a@b",
            "test@localhost",
            "ada@123",
            "ada@[127.0.0.1]",
            "ada@example.c",
            "ada@exa_mple.com",
            "\"weird name\"@example.com",
            "a..b@example.com",
            ".ada@example.com",
            "ada.@example.com",
            "ada@-example.com",
            "ada@example-.com",
            "ada@example..com",
            "ada@example.",
            "ada@.com",
            "ada",
            "@example.com",
            "ada@@example.com",
            "ada example@example.com",
            "   "
        );

        for (String address : addresses) {
            for (Function<String, Object> request : emailRequests()) {
                assertThat(violationsOn(request.apply(address), "email"))
                    .withFailMessage("an undeliverable address was accepted: %s", address)
                    .isNotEmpty();
            }
        }
    }

    @Test
    void theEmailLocalPartAndWholeAddressAreBothBounded() {
        String longLocal = "a".repeat(65) + "@example.com";
        String longAddress = "a".repeat(200) + "@" + "b".repeat(50) + ".com";

        assertThat(longAddress.length()).isGreaterThan(ValidationLimits.EMAIL_MAX);

        for (Function<String, Object> request : emailRequests()) {
            assertThat(violationsOn(request.apply(longLocal), "email")).isNotEmpty();
            assertThat(violationsOn(request.apply(longAddress), "email")).isNotEmpty();
        }
    }

    /** Every request that carries an email, so a rule cannot be applied to only some of them. */
    private List<Function<String, Object>> emailRequests() {
        return List.of(
            e -> new RegisterRequest("Ada Lovelace", e, "password123"),
            e -> new LoginRequest(e, "password123"),
            e -> new ForgotPasswordRequest(e),
            e -> new ResendVerificationRequest(e),
            e -> new ChangeEmailRequest(e)
        );
    }

    @Test
    void everyEndpointThatTakesAPasswordAppliesTheSameRule() {
        List<Function<String, Object>> requests = List.of(
            p -> new RegisterRequest("Ada Lovelace", "ada@example.com", p),
            p -> new LoginRequest("ada@example.com", p),
            p -> new ChangePasswordRequest("password123", p),
            p -> new ResetPasswordRequest("token", p)
        );

        // 30 characters of a three byte script: inside the character bound, past the byte one.
        String tooManyBytes = "密".repeat(30);

        assertThat(tooManyBytes.length()).isLessThanOrEqualTo(ValidationLimits.PASSWORD_MAX);
        assertThat(tooManyBytes.getBytes(StandardCharsets.UTF_8).length)
            .isGreaterThan(ValidationLimits.PASSWORD_MAX_BYTES);

        for (Function<String, Object> request : requests) {
            assertThat(violations(request.apply("password123"))).isEmpty();
            assertThat(violations(request.apply("short"))).isNotEmpty();
            assertThat(violations(request.apply("x".repeat(ValidationLimits.PASSWORD_MAX + 1)))).isNotEmpty();
            assertThat(violations(request.apply("   "))).isNotEmpty();
            assertThat(violations(request.apply(tooManyBytes))).isNotEmpty();
        }
    }

    @Test
    void aPasswordThatValidatesIsAlwaysOneBCryptWillHash() {
        // The character bound alone does not imply the byte bound, which is the whole reason
        // the byte constraint exists. Anything the rule accepts must fit what BCrypt hashes.
        Stream
            .of("password123", "p@ssw0rd", "密".repeat(24), "x".repeat(ValidationLimits.PASSWORD_MAX))
            .forEach(password -> {
                assertThat(violations(new ChangePasswordRequest("password123", password))).isEmpty();
                assertThat(password.getBytes(StandardCharsets.UTF_8).length)
                    .isLessThanOrEqualTo(ValidationLimits.PASSWORD_MAX_BYTES);
            });
    }

    /**
     * Asserts that a create DTO and an edit DTO answer the same way for one field.
     *
     * <p>A null is not compared: absent means "leave this alone" on an edit and "you left out
     * a required field" on a create, so the two are meant to differ there.</p>
     *
     * @param field the field both requests carry.
     * @param onCreate builds the create request around the value.
     * @param onEdit builds the edit request around the value.
     */
    private void assertAgree(String field, Function<String, Object> onCreate, Function<String, Object> onEdit) {
        List<String> values = List.of(
            "",
            "   ",
            "  padded  ",
            "A",
            "Ada Lovelace",
            "Ada 2",
            "x".repeat(ValidationLimits.TITLE_MAX),
            "x".repeat(ValidationLimits.TITLE_MAX + 1)
        );

        for (String value : values) {
            boolean createRejected = !violationsOn(onCreate.apply(value), field).isEmpty();
            boolean editRejected = !violationsOn(onEdit.apply(value), field).isEmpty();

            assertThat(editRejected)
                .withFailMessage(
                    "create and edit disagree on %s = \"%s\": create %s it, edit %s it",
                    field,
                    value,
                    createRejected ? "rejected" : "accepted",
                    editRejected ? "rejected" : "accepted"
                )
                .isEqualTo(createRejected);
        }
    }

    private Set<?> violations(Object request) {
        return validator.validate(request);
    }

    private List<?> violationsOn(Object request, String field) {
        return validator
            .validate(request)
            .stream()
            .filter(v -> v.getPropertyPath().toString().equals(field))
            .toList();
    }

    private CreateQuestionAnswer answer(String text, boolean correct) {
        return new CreateQuestionAnswer(text, correct);
    }

}
