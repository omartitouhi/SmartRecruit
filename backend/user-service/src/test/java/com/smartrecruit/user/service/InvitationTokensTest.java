package com.smartrecruit.user.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-COMP-03 — invitation tokens are random and stored hashed. */
class InvitationTokensTest {

    @Test
    void tokensAreUrlSafeAndDifferentEachTime() {
        String first = InvitationTokens.generate();
        String second = InvitationTokens.generate();

        assertThat(first).isNotEqualTo(second).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void hashIsAStableSha256HexAndNeverTheToken() {
        String token = InvitationTokens.generate();

        assertThat(InvitationTokens.hash(token)).isEqualTo(InvitationTokens.hash(token))
                .matches("[0-9a-f]{64}").isNotEqualTo(token);
        assertThat(InvitationTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
