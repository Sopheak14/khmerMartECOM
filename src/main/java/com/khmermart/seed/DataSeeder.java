package com.khmermart.seed;

import com.khmermart.modules.user.entity.User;
import com.khmermart.modules.user.model.Role;
import com.khmermart.modules.user.repository.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
public class DataSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(String... args) {
    seedUserIfMissing(
        "admin@khmermart.local", "Admin@12345", "Admin User", "+85512000001", Role.ADMIN);
    seedUserIfMissing(
        "staff@khmermart.local", "Staff@12345", "Staff User", "+85512000002", Role.STAFF);
    seedUserIfMissing(
        "customer1@khmermart.local",
        "Customer@123",
        "Sopheap Customer",
        "+85512345678",
        Role.CUSTOMER);
  }

  private void seedUserIfMissing(
      String email, String rawPassword, String fullName, String phone, Role role) {
    if (!userRepository.existsByEmail(email.toLowerCase())) {
      User user =
          User.builder()
              .email(email.toLowerCase())
              .passwordHash(passwordEncoder.encode(rawPassword))
              .fullName(fullName)
              .phone(phone)
              .role(role)
              .active(true)
              .emailVerifiedAt(Instant.now())
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();
      userRepository.save(user);
      log.info("Seeded demo account: {}", email);
    }
  }
}
