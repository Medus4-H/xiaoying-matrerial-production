package com.xiaoying.studio.identity;

final class ContactMasker {
  private ContactMasker() {}

  static String email(String value) {
    if (value == null) return null;
    int at = value.indexOf('@');
    if (at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1) return "***";
    int firstEnd = value.offsetByCodePoints(0, 1);
    String visible = firstEnd < at ? value.substring(0, firstEnd) : "";
    return visible + "***" + value.substring(at);
  }

  static String phone(String value) {
    if (value == null) return null;
    if (!value.matches("\\+?[0-9]{6,15}")) return "****";
    boolean international = value.startsWith("+");
    String digits = international ? value.substring(1) : value;
    String prefix = digits.length() >= 11 ? digits.substring(0, 3) : "";
    int suffixLength = digits.length() >= 11 ? 4 : 2;
    return (international ? "+" : "")
        + prefix
        + "****"
        + digits.substring(digits.length() - suffixLength);
  }
}
