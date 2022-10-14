package org.togetherjava.tjbot.commands.search;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * <pre>This class contains the logic required for the '/google' slash command.
 * The syntax of the command is as followed:
 *      /google query [search term]
 *
 * Results are sent to the user as embeds. The result contains the questions and answers
 * of the search results under "People also asked" alongside the first 5 organic search results.</pre>
 *
 * @author <a href="https://github.com/surajkumar">Suraj Kumar</a>
 */
public class GoogleCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GoogleCommand.class);
    /**
     * The option that contains the search term within the /google command.
     */
    private static final String COMMAND_OPTION = "query";
    /**
     * The maximum number of organic search results to show.
     * When there are less than 5 results available in the search results,
     * the amount returned is the number of available results. This flag does not affect the
     * "People also asked" results.
     */
    private static final int MAX_RESULTS_TO_SHOW = 5;
    /**
     * The search strategy that is going to return the results to us.
     */
    private final SearchStrategy searchStrategy = new GoogleSearchStrategy();

    /**
     * Constructs a new {@code GoogleCommand} object and sets up the metadata for this command including the name,
     * description and command options.
     */
    public GoogleCommand() {
        super("google", "Searches Google for your search query", CommandVisibility.GUILD);
        this.getData()
                .addOption(OptionType.STRING, COMMAND_OPTION, "the query to send to Google", true);
    }

    /**
     * <pre>The main logic for the /google command.
     * When we receive the event from discord, the reply is deferred for a later period of time. This is because
     * calling the {@link org.togetherjava.tjbot.commands.search.SearchStrategy} is considered a blocking operation.
     *
     * Related questions are sent as 1 long text block to the user as a response.
     * Organic Google search results are collated and send as separate embeds to the user as a response.</pre>
     *
     * @param event the event provided by Discord to ack the initialization of the slash command.
     */
    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        logger.info("Received /google slash command");

        event.deferReply().queue();
        String searchTerm = Objects.requireNonNull(event.getOption(COMMAND_OPTION)).getAsString();
        logger.info("{} entered search term {}", Objects.requireNonNull(event.getMember()).getEffectiveName(), searchTerm);

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
    private List<MessageEmbed> createMessageEmbedForOrganicResults(JSONArray searchResults) {
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
    private MessageEmbed createMessageEmbedForRelatedQuestions(JSONArray relatedQuestions, String searchTerm) {
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