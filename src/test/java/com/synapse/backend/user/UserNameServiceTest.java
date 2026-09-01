package com.synapse.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserNameServiceTest {

    private final UserNameService userNameService = new UserNameService();

    @Test
    void capitalisesEveryWordOfALowercaseName() {
        assertThat(userNameService.capitalised("ada lovelace")).isEqualTo("Ada Lovelace");
        assertThat(userNameService.capitalised("kenneth")).isEqualTo("Kenneth");
    }

    @Test
    void recasesANameTypedEntirelyInCapitals() {
        assertThat(userNameService.capitalised("ADA LOVELACE")).isEqualTo("Ada Lovelace");
    }

    @Test
    void leavesAWordThatWasDeliberatelyMixedCaseAlone() {
        assertThat(userNameService.capitalised("ada McDonald")).isEqualTo("Ada McDonald");
        assertThat(userNameService.capitalised("Ada van der Berg")).isEqualTo("Ada Van Der Berg");
    }

    @Test
    void capitalisesAfterHyphensAndApostrophes() {
        assertThat(userNameService.capitalised("jean-pierre o'brien")).isEqualTo("Jean-Pierre O'Brien");
        assertThat(userNameService.capitalised("JEAN-PIERRE")).isEqualTo("Jean-Pierre");
        assertThat(userNameService.capitalised("ada o’neill")).isEqualTo("Ada O’Neill");
    }

    @Test
    void collapsesSurroundingAndRepeatedWhitespace() {
        assertThat(userNameService.capitalised("  ada   lovelace  ")).isEqualTo("Ada Lovelace");
    }

    @Test
    void leavesANameThatIsAlreadyCapitalisedUnchanged() {
        assertThat(userNameService.capitalised("Ada Lovelace")).isEqualTo("Ada Lovelace");
    }

}
