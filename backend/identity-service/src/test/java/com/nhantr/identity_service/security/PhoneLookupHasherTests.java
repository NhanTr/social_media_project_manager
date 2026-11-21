package com.nhantr.identity_service.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneLookupHasherTests {
    private final PhoneLookupHasher hasher = new PhoneLookupHasher();

    @Test
    void normalizesVietnamesePhoneFormsToOneCanonicalValue() {
        assertThat(hasher.normalize("0901234567")).isEqualTo("+84901234567");
        assertThat(hasher.normalize("84901234567")).isEqualTo("+84901234567");
        assertThat(hasher.normalize("+84 901 234 567")).isEqualTo("+84901234567");
    }

    @Test
    void hashesCanonicalUtf8BytesAsLowercaseSha256() {
        assertThat(hasher.hash("0901234567"))
                .isEqualTo("0c450d4175db5d08e888c726767096ac29c6dfa0cc8e755e42af0629a1d7ca4c");
        assertThat(hasher.hash("+84 901 234 567"))
                .isEqualTo("0c450d4175db5d08e888c726767096ac29c6dfa0cc8e755e42af0629a1d7ca4c");
    }

    @Test
    void rejectsBlankAlphabeticTooShortAndPartialPhoneInput() {
        assertThatThrownBy(() -> hasher.normalize(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.normalize("phone-number"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.normalize("1234567"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.normalize("090123"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
