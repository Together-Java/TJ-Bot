package org.togetherjava.tjbot.commands.search;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GoogleCommand extends SlashCommandAdapter {
    private static final String COMMAND_OPTION = "query";
    private static final int MAX_RESULTS_TO_SHOW = 5;
    private final SearchStrategy searchStrategy = new GoogleSearchStrategy();

    public GoogleCommand() {
        super("google", "Searches Google for your search query", CommandVisibility.GUILD);
        this.getData()
            .addOption(OptionType.STRING, COMMAND_OPTION, "the query to send to Google", true);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        searchStrategy.search(Objects.requireNonNull(event.getOption(COMMAND_OPTION)).getAsString())
            .thenAccept(response -> {
                JSONObject json = new JSONObject(response.body());
                if (json.has("related_questions")) {
                    StringBuilder sb = new StringBuilder();
                    JSONArray relatedQuestions = json.getJSONArray("related_questions");
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
                            .append(snippetOfAnswer)
                            .append("\n")
                            .append(link)
                            .append("\n");
                    }
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setAuthor("Here are some answers that I have found", null, null);
                    eb.setDescription(sb);
                    eb.setColor(Color.RED);
                    event.getHook().editOriginalEmbeds().setEmbeds(eb.build()).queue();
                }
                if (json.has("organic_results")) {
                    List<MessageEmbed> embeds = new ArrayList<>();
                    JSONArray searchResults = json.getJSONArray("organic_results");
                    for (int i = 0; i < Math.min(searchResults.length(),
                            MAX_RESULTS_TO_SHOW); i++) {
                        JSONObject result = searchResults.getJSONObject(i);
                        String title = result.getString("title");
                        String link = result.getString("link");
                        String snippet = result.getString("snippet");
                        EmbedBuilder eb = new EmbedBuilder();
                        eb.setAuthor(title, link, null);
                        eb.setDescription(snippet);
                        eb.setColor(Color.RED);
                        embeds.add(eb.build());
                    }
                    if (embeds.size() > 0) {
                        event.getHook()
                            .sendMessage("Here are some results that I have found:")
                            .queue();
                        event.getHook().editOriginalEmbeds().setEmbeds(embeds).queue();
                    } else {
                        event.getHook()
                            .sendMessage(
                                    "I could not find any results for your search term. Try rephrasing your search.")
                            .queue();
                    }
                }
            });
    }
}