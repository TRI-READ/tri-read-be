package com.triread.api.group;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GroupInviteCodeServiceTest {

    private final GroupInviteCodeService inviteCodeService = new GroupInviteCodeService();

    @Test
    void generatedCodeUsesReadableTenCharacterFormat() {
        String code = inviteCodeService.generateCode();

        assertThat(code).matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}-"
                + "[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}");
        assertThat(inviteCodeService.normalize(code)).hasSize(10);
    }

    @Test
    void normalizeMakesPastedCodesEquivalentBeforeHashing() {
        String normalized = inviteCodeService.normalize(" abcd2 - efgh3 ");

        assertThat(normalized).isEqualTo("ABCD2EFGH3");
        assertThat(inviteCodeService.hash(normalized))
                .isEqualTo(inviteCodeService.hash("ABCD2EFGH3"));
    }
}
