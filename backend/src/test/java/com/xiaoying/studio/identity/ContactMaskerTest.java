package com.xiaoying.studio.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContactMaskerTest {
  @Test
  void masksPhoneNumbersIncludingShortAndInternationalNumbers() {
    assertThat(ContactMasker.phone("12345671234")).isEqualTo("123****1234");
    assertThat(ContactMasker.phone("+8613800138000")).isEqualTo("+861****8000");
    assertThat(ContactMasker.phone("123456")).isEqualTo("****56");
    assertThat(ContactMasker.phone("invalid")).isEqualTo("****");
    assertThat(ContactMasker.phone(null)).isNull();
  }

  @Test
  void masksEmailLocalPartIncludingSingleCharacterAndMalformedValues() {
    assertThat(ContactMasker.email("person@example.com")).isEqualTo("p***@example.com");
    assertThat(ContactMasker.email("x@example.com")).isEqualTo("***@example.com");
    assertThat(ContactMasker.email("invalid")).isEqualTo("***");
    assertThat(ContactMasker.email(null)).isNull();
  }
}
