package com.devpulse.integration.github;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a GitHub repository URL into its owner and repo halves.
 *
 * <p>This is the one place GitHub URL shape is known. auth-service stores the
 * URL verbatim and never parses it — deriving owner/repo is GitHub knowledge,
 * and it belongs to the service that talks to GitHub.
 *
 * <p>Accepted, all yielding {@code octocat} / {@code Hello-World}:
 * <pre>
 *   https://github.com/octocat/Hello-World
 *   http://github.com/octocat/Hello-World/
 *   https://www.github.com/octocat/Hello-World.git
 *   git@github.com:octocat/Hello-World.git
 *   github.com/octocat/Hello-World
 * </pre>
 *
 * <p>Rejected: deep paths ({@code /tree/main}, {@code /pull/3}), hosts other
 * than github.com, and anything with no repo segment. A deep path is rejected
 * rather than truncated — silently turning a pull-request link into a repo link
 * would sync a repository the admin never named.
 */
public final class GithubRepoUrlParser {

    private static final Pattern HTTP_FORM = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?github\\.com/"
                    + "(?<owner>[A-Za-z0-9][A-Za-z0-9-]*)/"
                    + "(?<repo>[A-Za-z0-9._-]+?)(?:\\.git)?/?$");

    private static final Pattern SSH_FORM = Pattern.compile(
            "^git@github\\.com:"
                    + "(?<owner>[A-Za-z0-9][A-Za-z0-9-]*)/"
                    + "(?<repo>[A-Za-z0-9._-]+?)(?:\\.git)?/?$");

    private GithubRepoUrlParser() {
    }

    /**
     * @return the parsed coordinates, or empty if the URL is not a plain
     *         repository URL
     */
    public static Optional<GithubRepoCoordinates> parse(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String trimmed = url.trim();

        Matcher matcher = HTTP_FORM.matcher(trimmed);
        if (!matcher.matches()) {
            matcher = SSH_FORM.matcher(trimmed);
            if (!matcher.matches()) {
                return Optional.empty();
            }
        }

        String owner = matcher.group("owner");
        String repo = matcher.group("repo");
        // "." and ".." are valid against the character class but are not repos.
        if (".".equals(repo) || "..".equals(repo)) {
            return Optional.empty();
        }
        return Optional.of(new GithubRepoCoordinates(owner, repo));
    }

    /**
     * A repository's owner and name, plus the canonical forms derived from
     * them.
     */
    public record GithubRepoCoordinates(String owner, String repo) {

        /** {@code owner/repo}, matching GitHub's own {@code full_name}. */
        public String fullName() {
            return owner + "/" + repo;
        }

        /** The canonical https URL, whatever form was supplied. */
        public String canonicalUrl() {
            return "https://github.com/" + owner + "/" + repo;
        }
    }
}
