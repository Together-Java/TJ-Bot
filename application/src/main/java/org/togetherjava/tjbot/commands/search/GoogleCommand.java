package org.togetherjava.tjbot.commands.search;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.config.Config;

import java.net.http.HttpResponse;
import java.util.Objects;
/**
 * <p>This class contains the logic required for the '/google' slash command.</p>
 * <p>The syntax of the command is as followed: /google query [search term].</p>
 * <p>Results are sent to the user as embeds. The result contains the questions and answers of the search results under
 * "People also asked" alongside the first 5 organic search results.</p>
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
     * The search strategy that is going to return the results to us.
     */
    private final SearchStrategy<HttpResponse<String>> searchStrategy;

    /**
     * <p>Constructs a new {@code GoogleCommand} object and sets up the metadata for this command including the name,
     * description and command options.</p>
     */
    public GoogleCommand(Config config) {
        super("google", "Searches Google for your search query", CommandVisibility.GUILD);
        this.getData()
                .addOption(OptionType.STRING, COMMAND_OPTION, "the query to send to Google", true);
        searchStrategy = new GoogleSearchStrategy(config.getSerpapiApiKey());
    }
    /**
     * <p>The main logic for the /google command.
     * When we receive the event from discord, the reply is deferred for a later period of time. This is because calling
     * the {@link org.togetherjava.tjbot.commands.search.SearchStrategy} is considered a blocking operation.</p>
     *
     * @param event the event provided by Discord to ack the initialization of the slash command.
     */
    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        logger.info("Received /google slash command");

        event.deferReply().queue();
        String searchTerm = Objects.requireNonNull(event.getOption(COMMAND_OPTION)).getAsString();
        logger.info("{} entered search term {}", Objects.requireNonNull(event.getMember()).getEffectiveName(), searchTerm);

        new GoogleResponseComposer().doSearchAndSendResponse(searchStrategy, searchTerm, event);
    }
}