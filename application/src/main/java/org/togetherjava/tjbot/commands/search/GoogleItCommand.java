package org.togetherjava.tjbot.commands.search;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

/**
 * <p>This class is designed for specific use in the #active_questions channel within the Together Java Discord server.
 * As such, it is a convenience command that allows users to search their questions (thread title) on Google.</p>
 *
 * @author <a href="https://github.com/surajkumar">Suraj Kumar</a>
 */
public class GoogleItCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GoogleItCommand.class);
    /**
     * The search strategy that is going to return the results to us.
     */
    private final SearchStrategy searchStrategy = new GoogleSearchStrategy();

    /** The error message displayed to the user if they are not within a thread in #active_questions. */
    private static final String WRONG_CHANNEL_ERROR = "You must be within a thread in #active_questions to run this command.";

    /**
     * <p>Constructs a new {@code GoogleItCommand} object and sets up the metadata for this command including the name,
     * description</p>
     */
    public GoogleItCommand() {
        super("googleit", "Searches the channel title on Google", CommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        logger.info("Received /googleit slash command");
        try {
            String parent = event.getChannel().asThreadChannel().getParentChannel().getName();
            if(parent.equals("active_questions")) {
                String searchTerm = event.getChannel().asThreadChannel().getName();
                event.deferReply().queue();
                new GoogleResponseComposer().doSearchAndSendResponse(searchStrategy, searchTerm, event);
            } else {
                event.reply(WRONG_CHANNEL_ERROR).queue();
            }
        } catch (IllegalStateException ex) {
            event.reply(WRONG_CHANNEL_ERROR).queue();
        }
    }
}