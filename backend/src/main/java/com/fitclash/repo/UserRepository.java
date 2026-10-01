// File: src/main/java/com/fitclash/repo/UserRepository.java
package com.fitclash.repo;

import com.fitclash.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc(String fragment);
}
