package org.togetherjava.tjbot.features.xkcd;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openai.models.files.FilePurpose;
import org.apache.commons.lang3.IntegerRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.chatgpt.ChatGptService;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Retrieves and caches XKCD comic posts from the official XKCD JSON API.
 * <p>
 * This class handles fetching XKCD comics (1-{@value #XKCD_POSTS_AMOUNT}, excluding the joke comic
 * #404) using concurrent HTTP requests with rate limiting via semaphore and thread pool.
 * <p>
 * Posts are cached locally in {@value #SAVED_XKCD_PATH} as JSON and uploaded to OpenAI using the
 * provided {@link ChatGptService} if not already present.
 */
public class XkcdService {

    private static final Logger logger = LoggerFactory.getLogger(XkcdService.class);

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String XKCD_GET_URL = "https://xkcd.com/%d/info.0.json";
    private static final String SAVED_XKCD_PATH = "xkcd.generated.json";
    private static final int XKCD_POSTS_AMOUNT = 3201;
    private static final int FETCH_XKCD_POSTS_SEMAPHORE_SIZE = 10;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Integer, XkcdPost> xkcdPosts = new HashMap<>();
    private final ChatGptService chatGptService;
    private String xkcdUploadedFileId;

    public XkcdService(ChatGptService chatGptService) {
        this.chatGptService = chatGptService;

        Optional<String> xkcdUploadedFileIdOptional =
                chatGptService.getUploadedFileId(SAVED_XKCD_PATH);

        if (xkcdUploadedFileIdOptional.isPresent()) {
            logger.info("XKCD posts file {} is already uploaded", SAVED_XKCD_PATH);
            xkcdUploadedFileId = xkcdUploadedFileIdOptional.get();
        }

        Path savedXckdsPath = Path.of(SAVED_XKCD_PATH);
        if (savedXckdsPath.toFile().exists()) {
            populateXkcdPostsFromFile(savedXckdsPath);

            if (xkcdUploadedFileIdOptional.isEmpty()) {
                logger.info(
                        "Will attempt to upload XKCD posts from existing file '{}' since it is not uploaded",
                        SAVED_XKCD_PATH);
                uploadXkcdFile(savedXckdsPath);
            }
            return;
        }

        logger.info("Could not find XKCD posts locally saved in '{}' so will fetch...",
                SAVED_XKCD_PATH);
        fetchAllXkcdPosts(savedXckdsPath);
    }

    public Optional<XkcdPost> getXkcdPost(int id) {
        return Optional.ofNullable(xkcdPosts.get(id));
    }

    public String getXkcdUploadedFileId() {
        return xkcdUploadedFileId;
    }

    public Map<Integer, XkcdPost> getXkcdPosts() {
        return xkcdPosts;
    }

    private void fetchAllXkcdPosts(Path savedXckdsPath) {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        logger.info("Fetching {} XKCD posts...", XKCD_POSTS_AMOUNT);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore semaphore = new Semaphore(FETCH_XKCD_POSTS_SEMAPHORE_SIZE);
            List<? extends Future<?>> futures = IntegerRange.of(1, XKCD_POSTS_AMOUNT)
                .toIntStream()
                .filter(id -> id != 404) // XKCD has a joke on comic ID 404 so exclude
                .mapToObj(xkcdId -> executor.submit(() -> {
                    semaphore.acquireUninterruptibly();
                    retrieveXkcdPost(xkcdId).join().ifPresent(post -> xkcdPosts.put(xkcdId, post));
                    semaphore.release();
                }))
                .toList();

            try {
                for (Future<?> future : futures) {
                    future.get();
                }
            } catch (InterruptedException e) {
                logger.error("Failed to wait for future", e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                logger.error("Could not get result from future", e);
            }
        }

        saveToFile(savedXckdsPath, xkcdPosts);
        uploadXkcdFile(savedXckdsPath);
        logger.info("Done. Fetched {} XKCD posts and saving to '{}'.", xkcdPosts.size(),
                SAVED_XKCD_PATH);
    }

    private CompletableFuture<Optional<XkcdPost>> retrieveXkcdPost(int id) {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(String.format(XKCD_GET_URL, id))).build();

        logger.debug("Retrieving XKCD post {}...", id);

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                int statusCode = response.statusCode();

                if (statusCode < HttpURLConnection.HTTP_OK) {
                    logger.warn("Tried to retrieve XKCD post, but failed with status code: {}",
                            statusCode);
                    return Optional.empty();
                }

                try {
                    return Optional.of(objectMapper.readValue(response.body(), XkcdPost.class));
                } catch (IOException e) {
                    logger.error("Tried to parse XKCD post but failed, response body: {}",
                            response.body(), e);
                    return Optional.empty();
                }
            });
    }

    private void uploadXkcdFile(Path savedXckdsPath) {
        Optional<String> fileIdOptional =
                chatGptService.uploadFileIfNotExists(savedXckdsPath, FilePurpose.USER_DATA);

        if (fileIdOptional.isEmpty()) {
            return;
        }

        String fileId = fileIdOptional.get();
        logger.info("XKCD posts have been uploaded with ID '{}'", fileId);

        xkcdUploadedFileId = fileId;

    }

    private void saveToFile(Path path, Map<Integer, XkcdPost> posts) {
        try {
            objectMapper.writeValue(path.toFile(), posts);
            logger.info("Saved XKCD posts to '{}'", path);
        } catch (IOException e) {
            logger.error("Failed to save XKCD posts to {}", path, e);
        }
    }

    private void populateXkcdPostsFromFile(Path path) {
        try {
            String jsonContent = Files.readString(path);

            Map<Integer, XkcdPost> loadedPosts =
                    objectMapper.readValue(jsonContent, new TypeReference<>() {});

            xkcdPosts.clear();
            xkcdPosts.putAll(loadedPosts);

            logger.info("Loaded {} XKCD posts from {}", xkcdPosts.size(), path);
        } catch (IOException e) {
            logger.error("Failed to load XKCD posts from {}", path, e);
        }
    }
}
