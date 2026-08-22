package com.devpulse.auth.repository;

import com.devpulse.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for the {@code users} table.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    /**
     * Case-insensitive lookup, for the paths that must not create a second row
     * for an address that already exists in a different case. {@code users.email}
     * is UNIQUE case-sensitively, so 'A@x.com' and 'a@x.com' can both be stored
     * today; matching case-insensitively is what keeps an invite from being
     * raised against an account that is already there.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);
}
