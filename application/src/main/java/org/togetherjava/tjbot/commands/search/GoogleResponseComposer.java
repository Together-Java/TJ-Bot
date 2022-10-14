package org.togetherjava.tjbot.commands.search;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>A common class that is used for sending responses to Discord for a Google search request. This class contains
 * functionality that allows you to get pre-constructed {@code MessageEmbed}s for dispatch to the user. Additionally,
 * you can allow this composer class to do that for you.</p>
 *
 * @author <a href="https://github.com/surajkumar">Suraj Kumar</a>
 */
public class GoogleResponseComposer {
    private static final Logger logger = LoggerFactory.getLogger(GoogleResponseComposer.class);
    /**
     * The maximum number of organic search results to show.
     * When there are less than 5 results available in the search results,
     * the amount returned is the number of available results. This flag does not affect the
     * "People also asked" results.
     */
    private static final int MAX_RESULTS_TO_SHOW = 5;

    public GoogleResponseComposer() { }

    /**
     * <p>The main logic for the Google searching functionality. This method runs the Google search API, constructs
     * a response and sends it to the user who invoked this request.</p>
     *
     * @param searchStrategy The strategy to perform the search against.
     * @param searchTerm The query to search on Google.
     * @param event The Discord event so that a response can be sent.
     */
    public void doSearchAndSendResponse(SearchStrategy searchStrategy, String searchTerm, SlashCommandInteractionEvent event) {
        searchStrategy.search(searchTerm)
                .thenAccept(response -> {
                    MessageEmbed relatedQuestionsMessageEmbed = null;
                    JSONObject json = new JSONObject(response.body());
                    if (json.has("related_questions")) {
                        logger.info("related_questions present within response from Google, adding results to response");
                        relatedQuestionsMessageEmbed = createMessageEmbedForRelatedQuestions(json.getJSONArray("related_questions"), searchTerm);
                    }
                    if (json.has("organic_results")) {
                        logger.info("organic_results present within response from Google, adding results to response");
                        List<MessageEmbed> embeds = createMessageEmbedForOrganicResults(json.getJSONArray("organic_results"));
                        if(relatedQuestionsMessageEmbed != null) {
                            Collections.reverse(embeds);
                            embeds.add(relatedQuestionsMessageEmbed);
                            Collections.reverse(embeds);
                        }
                        if (embeds.size() > 0) {
                            event.getHook().editOriginalEmbeds().setEmbeds(embeds).queue();
                        } else {
                            event.getHook()
                                    .sendMessage("I could not find any results for your search term. Try rephrasing your search.")
                                    .queue();
                        }
                    } else if(relatedQuestionsMessageEmbed != null) {
                        event.getHook().editOriginalEmbeds().setEmbeds(relatedQuestionsMessageEmbed).queue();
                    }
                    logger.info("Finished handling /google slash command.");
                });
    }

    /**
     * Creates a ready to send response containing the information for an organic search result.
     * The response is set to be a separate message for each result.
     *
     * @param searchResults An array of organic search results to build the MessageEmbeds.
     * @return A List of MessageEmbeds for dispatch.
     */
    public List<MessageEmbed> createMessageEmbedForOrganicResults(JSONArray searchResults) {
        logger.info("Creating a list of MessageEmbed for a total of {} results", searchResults.length());
        List<MessageEmbed> embeds = new ArrayList<>();
        for (int i = 0; i < Math.min(searchResults.length(), MAX_RESULTS_TO_SHOW); i++) {
            JSONObject result = searchResults.getJSONObject(i);
            String title = result.getString("title");
            String link = result.getString("link");
            String snippet = result.getString("snippet");
            EmbedBuilder eb = new EmbedBuilder();
            eb.setAuthor(title, link, null);
            eb.setDescription(snippet);
            eb.setColor(Color.BLACK);
            embeds.add(eb.build());
        }
        return embeds;
    }

    /**
     * Creates a ready to send response containing the information for related questions search result.
     * The response is set to be 1 single message containing all the information.
     *
     * @param relatedQuestions An array of related questions search results to build the MessageEmbeds.
     * @return A List of MessageEmbeds for dispatch.
     */
    public MessageEmbed createMessageEmbedForRelatedQuestions(JSONArray relatedQuestions, String searchTerm) {
        logger.info("Creating a MessageEmbed for a total of {} results", relatedQuestions.length());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < relatedQuestions.length(); i++) {
            JSONObject result = relatedQuestions.getJSONObject(i);
            String question = result.getString("question");
            String snippetOfAnswer = "";
            if (result.has("snippet")) {
                snippetOfAnswer = result.getString("snippet");
            } else if (result.has("list")) {
                StringBuilder list = new StringBuilder();
                result.getJSONArray("list")
                        .toList()
                        .forEach(line -> list.append(line).append("\n"));
                snippetOfAnswer = list.toString();
            }
            String link = result.getString("link");
            sb.append("\n**")
                    .append(question)
                    .append("**")
                    .append("\n")
                    .append(snippetOfAnswer.trim())
                    .append("\n\n")
                    .append(link)
                    .append("\n");
        }
        return new EmbedBuilder()
                .setAuthor("Here are some answers that I have found for " + searchTerm, null, null)
                .setDescription(sb)
                .setColor(Color.BLACK)
                .build();
    }
}