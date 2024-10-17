package org.togetherjava.tjbot.features.github.projectnotification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to fetch pull requests for a given repository. When using the GitHub API a
 * valid PAT (personal access token) is required, and it must not be expired. Expired PAT will
 * result in HTTP 401.
 *
 * @author Suraj Kumar
 */
public class PullRequestFetcher {
    private static final Logger logger = LoggerFactory.getLogger(PullRequestFetcher.class);
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String GITHUB_API_URL = "https://api.github.com/repos/%s/%s/pulls";

    private final String githubPersonalAccessToken;
    private final HttpClient httpClient;

    /**
     * Constructs a PullRequestFetcher
     *
     * @param githubPersonalAccessToken The PAT used to authenticate against the GitHub API
     */
    public PullRequestFetcher(String githubPersonalAccessToken) {
        this.githubPersonalAccessToken = githubPersonalAccessToken;
        this.httpClient = HttpClient.newBuilder().build();
    }

    /**
     * Makes a request to the https://api.github.com/repos/%s/%s/pulls API and returns the result as
     * a List of PullRequest objects.
     *
     * On any API error, this code will not throw. Instead, a warning/error level log message is
     * sent. In this situation an empty List will be returned.
     *
     * @param repositoryOwner The owner of the GitHub repository
     * @param repositoryName The repository name
     * @return A List of PullRequest objects
     */
    public List<PullRequest> fetchPullRequests(String repositoryOwner, String repositoryName) {
        logger.trace(
                "Entry PullRequestFetcher#fetchPullRequests repositoryOwner={}, repositoryName={}",
                repositoryOwner, repositoryName);
        List<PullRequest> pullRequests = new ArrayList<>();
        HttpResponse<String> response = callGitHubRepoAPI(repositoryOwner, repositoryName);

        if (response == null) {
            logger.warn(
                    "Failed to make the request to the GitHub API which resulted in a null response");
            logger.trace("Exit PullRequestFetcher#fetchPullRequests");
            return pullRequests;
        }

        int statusCode = response.statusCode();
        logger.debug("Received http status {}", statusCode);

        if (statusCode == 200) {
            try {
                pullRequests = OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
            } catch (JsonProcessingException jpe) {
                logger.error("Failed to parse JSON", jpe);
            }
        } else {
            logger.warn("Unexpected HTTP status {} while fetching pull requests for {}, body={}",
                    statusCode, repositoryName, response.body());
        }

        logger.trace("Exit PullRequestFetcher#fetchPullRequests");
        return pullRequests;
    }

    private HttpResponse<String> callGitHubRepoAPI(String repositoryOwner, String repositoryName) {
        logger.trace(
                "Entry PullRequestFetcher#callGitHubRepoAPI repositoryOwner={}, repositoryName={}",
                repositoryOwner, repositoryName);
        String apiURL = GITHUB_API_URL.formatted(repositoryOwner, repositoryName);

        HttpResponse<String> response;

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(apiURL))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Authorization", githubPersonalAccessToken)
            .build();

        try {
            logger.trace("Sending request to {}", apiURL);
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            logger.debug("Received response httpStatus={} body={}", response.statusCode(),
                    response.body());
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to fetch pull request from discord for {}/{}: {}", repositoryOwner,
                    repositoryName, e);
            response = null;
        }

        logger.trace("Exit PullRequestFetcher#callGitHubRepoAPI");
        return response;
    }

    /**
     * Check if we can read a repositories pull request
     *
     * @param repositoryOwner The repository owner name
     * @param repositoryName The repository name
     * @return True if we can access the pull requests
     */
    public boolean isRepositoryAccessible(String repositoryOwner, String repositoryName) {
        logger.trace(
                "Entry isRepositoryAccessible#isRepositoryAccessible repositoryOwner={}, repositoryName={}",
                repositoryOwner, repositoryName);
        HttpResponse<String> response = callGitHubRepoAPI(repositoryOwner, repositoryName);
        logger.trace("Exit isRepositoryAccessible#isRepositoryAccessible");
        return response != null && response.statusCode() == 200;
    }
}
