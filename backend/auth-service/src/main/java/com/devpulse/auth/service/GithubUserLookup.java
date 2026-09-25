package com.devpulse.auth.service;

import java.util.Optional;

/** Resolves a GitHub username to that account's numeric id. */
public interface GithubUserLookup {

    /** Empty when no such GitHub user exists. Throws when GitHub cannot be reached. */
    Optional<GithubAccount> findByUsername(String username);

    record GithubAccount(long id, String login) {
    }
}
