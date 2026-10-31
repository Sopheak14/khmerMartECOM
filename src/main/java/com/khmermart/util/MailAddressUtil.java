package com.khmermart.util;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;

public final class MailAddressUtil {

  private MailAddressUtil() {}

  /**
   * Parses a MAIL_FROM value such as {@code KhmerMart <no-reply@khmermart.local>} (or a bare
   * address) into an InternetAddress, tolerating surrounding quotes/whitespace that .env files
   * sometimes add.
   */
  public static InternetAddress parseFrom(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      value = value.substring(1, value.length() - 1).trim();
    }
    try {
      InternetAddress[] parsed = InternetAddress.parse(value, false);
      if (parsed.length == 0) {
        throw new IllegalArgumentException("MAIL_FROM is empty");
      }
      InternetAddress addr = parsed[0];
      if (addr.getPersonal() != null) {
        addr.setPersonal(addr.getPersonal(), "UTF-8");
      }
      return addr;
    } catch (AddressException | UnsupportedEncodingException e) {
      throw new IllegalArgumentException("Invalid MAIL_FROM '" + value + "': " + e.getMessage(), e);
    }
  }
}
