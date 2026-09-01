package com.synapse.backend.user;

import java.util.Locale;

import org.springframework.stereotype.Service;

/**
 * The one place a user's full name is capitalised.
 *
 * <p>People type their name into a form in whatever case is convenient, so a name
 * is stored with each word capitalised rather than exactly as it was typed. A word
 * that was typed in a mixture of cases is left alone, because that mixture is
 * normally deliberate: lowercasing it would turn McDonald into Mcdonald.</p>
 */
@Service
public class UserNameService {

    /** Characters that start a new word inside one, as in Jean-Pierre and O'Brien. */
    private static final String WORD_SEPARATORS = "-'’";

    /**
     * Capitalises each word of a full name.
     *
     * <p>Surrounding and repeated whitespace is collapsed on the way through, so a
     * padded name is stored the way it is displayed.</p>
     *
     * @param fullName the name as the client supplied it.
     * @return the name with each word capitalised.
     */
    public String capitalised(String fullName) {
        StringBuilder capitalised = new StringBuilder();

        for (String word : fullName.strip().split("\\s+")) {
            if (!capitalised.isEmpty())
                capitalised.append(' ');

            capitalised.append(capitalisedWord(word));
        }

        return capitalised.toString();
    }

    /**
     * Capitalises one word, lowercasing the rest of it only when the whole word was
     * typed in a single case. "ada" and "ADA" both become "Ada", while "McDonald"
     * keeps the capital its owner meant.
     */
    private String capitalisedWord(String word) {
        StringBuilder capitalised = new StringBuilder(
            isSingleCase(word) ? word.toLowerCase(Locale.ROOT) : word
        );

        for (int i = 0; i < capitalised.length(); i++) {
            if (i == 0 || WORD_SEPARATORS.indexOf(capitalised.charAt(i - 1)) >= 0)
                capitalised.setCharAt(i, Character.toUpperCase(capitalised.charAt(i)));
        }

        return capitalised.toString();
    }

    private boolean isSingleCase(String word) {
        return word.equals(word.toLowerCase(Locale.ROOT)) || word.equals(word.toUpperCase(Locale.ROOT));
    }

}
