package com.devpulse.auth.service;

import com.devpulse.auth.dto.LinkGithubResponse;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ConflictException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links a DevPulse user to their GitHub account by saving its numeric id in
 * {@code users.github_id}. That id is what metrics-service matches a pull
 * request's or commit's author against, so without it their work arrives with
 * no author.
 *
 * <p>The username is <b>not verified</b> as belonging to the caller: there is no
 * GitHub OAuth yet. The guard against abuse is that one GitHub account can be
 * linked to only one user - a second claim is refused, not overwritten.
 */
@Service
public class GithubIdentityService {

    private static final Logger log = LoggerFactory.getLogger(GithubIdentityService.class);

    private final UserRepository userRepository;
    private final GithubUserLookup githubUserLookup;

    public GithubIdentityService(UserRepository userRepository, GithubUserLookup githubUserLookup) {
        this.userRepository = userRepository;
        this.githubUserLookup = githubUserLookup;
    }

    @Transactional
    public LinkGithubResponse link(Integer userId, String username) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        GithubUserLookup.GithubAccount account = githubUserLookup.findByUsername(username.trim())
                .orElseThrow(() -> new ResourceNotFoundException("GitHub user", username));

        userRepository.findFirstByGithubId(account.id())
                .filter(owner -> !owner.getUserId().equals(userId))
                .ifPresent(owner -> {
                    throw new ConflictException(
                            "That GitHub account is already linked to another DevPulse user");
                });

        user.setGithubId(account.id());
        userRepository.save(user);
        log.info("User {} linked GitHub account {} (id {})", userId, account.login(), account.id());
        return new LinkGithubResponse(account.id(), account.login());
    }
}
