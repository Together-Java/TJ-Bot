package org.togetherjava.tjbot.commands.tags;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.utils.StringDistances;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Implements the {@code /tag} command which lets the bot respond content of a tag that has been
 * added previously.
 * <p>
 * Tags can be added by using {@link TagManageCommand} and a list of all tags is available using
 * {@link TagsCommand}.
 */
public final class TagCommand extends SlashCommandAdapter {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private final TagSystem tagSystem;
    private static final int MAX_SUGGESTIONS = 5;
    static final String ID_OPTION = "id";
    static final String REPLY_TO_USER_OPTION = "reply-to";

    /**
     * Creates a new instance, using the given tag system as base.
     *
     * @param tagSystem the system providing the actual tag data
     */
    public TagCommand(TagSystem tagSystem) {
        super("tag", "Display a tags content", CommandVisibility.GUILD);

        this.tagSystem = tagSystem;

        getData().addOptions(
                new OptionData(OptionType.STRING, ID_OPTION, "The id of the tag to display", true,
                        true),
                new OptionData(OptionType.USER, REPLY_TO_USER_OPTION,
                        "Optionally, the user who you want to reply to", false));
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();

        if (!focusedOption.getName().equals(ID_OPTION)) {
            throw new IllegalArgumentException(
                    "Unexpected option, was: " + focusedOption.getName());
        }

        Collection<Command.Choice> choices = StringDistances
            .closeMatches(focusedOption.getValue(), tagSystem.getAllIds(), MAX_SUGGESTIONS)
            .stream()
            .map(id -> new Command.Choice(id, id))
            .toList();

        event.replyChoices(choices).queue();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String id = event.getOption(ID_OPTION).getAsString();
        OptionMapping replyToUserOption = event.getOption(REPLY_TO_USER_OPTION);

        if (tagSystem.handleIsUnknownTag(id, event)) {
            return;
        }

        String tagContent = tagSystem.getTag(id).orElseThrow();
        MessageEmbed contentEmbed = new EmbedBuilder().setDescription(tagContent)
            .setFooter(event.getUser().getName() + " • used " + event.getCommandString())
            .setTimestamp(Instant.now())
            .setColor(TagSystem.AMBIENT_COLOR)
            .build();

        Optional<String> replyToUserMention = Optional.ofNullable(replyToUserOption)
            .map(OptionMapping::getAsUser)
            .map(User::getAsMention);

        List<String> links =
                extractLinks(tagContent).stream().limit(Message.MAX_EMBED_COUNT - 1L).toList();
        if (links.isEmpty()) {
            // No link previews
            ReplyCallbackAction message = event.replyEmbeds(contentEmbed);
            replyToUserMention.ifPresent(message::setContent);
            message.queue();
            return;
        }

        // Compute link previews
        event.deferReply().queue();

        // TODO Move all that link preview stuff into some helper
        createLinkPreviews(links).thenAccept(linkPreviews -> {
            if (linkPreviews.isEmpty()) {
                // Did not find any previews
                MessageEditBuilder message = new MessageEditBuilder().setEmbeds(contentEmbed);
                replyToUserMention.ifPresent(message::setContent);
                event.getHook().editOriginal(message.build()).queue();
                return;
            }

            Collection<MessageEmbed> embeds = new ArrayList<>();
            embeds.add(contentEmbed);
            embeds.addAll(linkPreviews.stream().map(LinkPreview::embed).toList());

            List<FileUpload> attachments =
                    linkPreviews.stream().map(LinkPreview::attachment).toList();

            MessageEditBuilder message =
                    new MessageEditBuilder().setEmbeds(embeds).setFiles(attachments);
            replyToUserMention.ifPresent(message::setContent);
            event.getHook().editOriginal(message.build()).queue();
        });
    }

    private static List<String> extractLinks(String content) {
        return new UrlDetector(content, UrlDetectorOptions.BRACKET_MATCH).detect()
            .stream()
            .map(Url::getFullUrl)
            .toList();
    }

    private static CompletableFuture<List<LinkPreview>> createLinkPreviews(List<String> links) {
        // TODO This stuff needs some polishing, barely readable
        List<CompletableFuture<Optional<LinkPreview>>> tasks = IntStream.range(0, links.size())
            .mapToObj(i -> createLinkPreview(links.get(i), i + ".png"))
            .toList();

        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
            .thenApply(any -> tasks.stream()
                .filter(Predicate.not(CompletableFuture::isCompletedExceptionally))
                .map(CompletableFuture::join)
                .flatMap(Optional::stream)
                .toList());
    }

    private record LinkPreview(FileUpload attachment, MessageEmbed embed) {
        static LinkPreview ofContents(@Nullable String title, String url,
                @Nullable String description, String thumbnailName, InputStream thumbnail) {
            FileUpload attachment = FileUpload.fromData(thumbnail, thumbnailName);
            MessageEmbed embed = new EmbedBuilder().setTitle(title, url)
                .setDescription(description)
                .setThumbnail("attachment://" + thumbnailName)
                .setColor(TagSystem.AMBIENT_COLOR)
                .build();

            return new LinkPreview(attachment, embed);
        }

        static LinkPreview ofThumbnail(String thumbnailName, InputStream thumbnail) {
            FileUpload attachment = FileUpload.fromData(thumbnail, thumbnailName);
            MessageEmbed embed = new EmbedBuilder().setThumbnail("attachment://" + thumbnailName)
                .setColor(TagSystem.AMBIENT_COLOR)
                .build();

            return new LinkPreview(attachment, embed);
        }
    }

    private static CompletableFuture<Optional<LinkPreview>> createLinkPreview(String link,
            String attachmentName) {
        URI linkAsUri;
        try {
            linkAsUri = URI.create(link);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        HttpRequest request = HttpRequest.newBuilder(linkAsUri).build();
        CompletableFuture<HttpResponse<InputStream>> task =
                CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());

        return task.thenCompose(response -> {
            int statusCode = response.statusCode();
            if (statusCode < HttpURLConnection.HTTP_OK
                    || statusCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            if (isResponseOfType(response, "image")) {
                return CompletableFuture.completedFuture(
                        Optional.of(LinkPreview.ofThumbnail(attachmentName, response.body())));
            }
            if (isResponseOfType(response, "text/html")) {
                return parseWebsite(link, attachmentName, response.body());
            }

            return CompletableFuture.completedFuture(Optional.empty());
        });
    }

    private static boolean isResponseOfType(HttpResponse<?> response, String type) {
        return response.headers()
            .firstValue("Content-Type")
            .filter(contentType -> contentType.startsWith(type))
            .isPresent();
    }

    private static CompletableFuture<Optional<LinkPreview>> parseWebsite(String link,
            String attachmentName, InputStream websiteContent) {
        Document doc;
        try {
            doc = Jsoup.parse(websiteContent, null, link);
        } catch (IOException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String title = parseOpenGraphTwitterMeta(doc, "title", doc.title()).orElse(null);
        String description =
                parseOpenGraphTwitterMeta(doc, "description", doc.title()).orElse(null);
        String image = parseOpenGraphMeta(doc, "image").orElse(null);

        if (image == null) {
            // TODO Can still do something
            return CompletableFuture.completedFuture(Optional.empty());
        }

        // TODO Massive duplication
        URI imageAsUri;
        try {
            imageAsUri = URI.create(image);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        HttpRequest request = HttpRequest.newBuilder(imageAsUri).build();
        CompletableFuture<HttpResponse<InputStream>> task =
                CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());

        return task.thenCompose(response -> {
            int statusCode = response.statusCode();
            if (statusCode < HttpURLConnection.HTTP_OK
                    || statusCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            if (!isResponseOfType(response, "image")) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            return CompletableFuture.completedFuture(Optional.of(LinkPreview.ofContents(title, link,
                    description, attachmentName, response.body())));
        });
    }

    private static Optional<String> parseOpenGraphTwitterMeta(Document doc, String metaProperty,
            @Nullable String fallback) {
        String value = Optional
            .ofNullable(doc.selectFirst("meta[property=og:%s]".formatted(metaProperty)))
            .or(() -> Optional
                .ofNullable(doc.selectFirst("meta[property=twitter:%s".formatted(metaProperty))))
            .map(element -> element.attr("content"))
            .orElse(fallback);
        if (value == null) {
            return Optional.empty();
        }
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<String> parseOpenGraphMeta(Document doc, String metaProperty) {
        return Optional.ofNullable(doc.selectFirst("meta[property=og:%s]".formatted(metaProperty)))
            .map(element -> element.attr("content"))
            .filter(Predicate.not(String::isBlank));
    }
}
