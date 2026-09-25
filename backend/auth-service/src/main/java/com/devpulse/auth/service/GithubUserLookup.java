package com.devpulse.auth.service;

import java.util.Optional;

/** Resolves a GitHub username to that account's numeric id. */
public interface GithubUserLookup {

    /** Empty when no such GitHub user exists. Throws when GitHub cannot be reached. */
    Optional<GithubAccount> findByUsername(String username);

    /** What GitHub publicly says about an account: enough for a person to recognise it as theirs. */
    record GithubAccount(long id, String login, String name, String avatarUrl, String profileUrl) {

        public GithubAccount(long id, String login) {
            this(id, login, null, null, null);
        }
    }
}
