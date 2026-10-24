package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.HelpSystemConfig;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.HelpThreads;
import org.togetherjava.tjbot.features.chatgpt.ChatGptModel;
import org.togetherjava.tjbot.features.chatgpt.ChatGptService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class HelpSystemHelperTest {
    private static final String ORIGINAL_ANSWER = """
            Useful links:
            - https://broken.example
            - https://working.example
            """;
    private static final String SANITIZED_ANSWER = """
            Useful links:
            - (broken link removed)
            - https://working.example
            """;
    private HelpSystemHelper helper;
    private SelfUser selfUser;

    @BeforeEach
    void setUp() {
        Config config = mock(Config.class);
        HelpSystemConfig helpSystemConfig = mock(HelpSystemConfig.class);
        when(config.getHelpSystem()).thenReturn(helpSystemConfig);
        when(config.getTagManageRolePattern()).thenReturn("tag-manage");
        when(helpSystemConfig.getHelpForumPattern()).thenReturn("questions");
        when(helpSystemConfig.getCategories()).thenReturn(List.of("java"));
        when(helpSystemConfig.getCategoryRoleSuffix()).thenReturn(" helper");

        Database database = Database.createMemoryDatabase(HelpThreads.HELP_THREADS);
        ChatGptService chatGptService = mock(ChatGptService.class);
        helper = new HelpSystemHelper(config, database, chatGptService, answer -> CompletableFuture
            .completedFuture(answer.replace("https://broken.example", "(broken link removed)")));

        selfUser = mock(SelfUser.class);
        when(selfUser.getName()).thenReturn("TJ-Bot");
        when(selfUser.getEffectiveAvatarUrl()).thenReturn("https://example.com/avatar.png");
    }

    @Test
    @DisplayName("Replaces broken links before building AI response embed")
    void replacesBrokenLinksInGeneratedEmbed() {
        MessageEmbed embed = helper.generateGptResponseEmbed(ORIGINAL_ANSWER, selfUser,
                "example question", ChatGptModel.FAST);

        assertEquals(SANITIZED_ANSWER, embed.getDescription());
    }

    @Test
    @DisplayName("Keeps original answer if broken-link replacement fails")
    void keepsOriginalAnswerWhenReplacementFails() {
        Config config = mock(Config.class);
        HelpSystemConfig helpSystemConfig = mock(HelpSystemConfig.class);
        when(config.getHelpSystem()).thenReturn(helpSystemConfig);
        when(config.getTagManageRolePattern()).thenReturn("tag-manage");
        when(helpSystemConfig.getHelpForumPattern()).thenReturn("questions");
        when(helpSystemConfig.getCategories()).thenReturn(List.of("java"));
        when(helpSystemConfig.getCategoryRoleSuffix()).thenReturn(" helper");

        Database database = Database.createMemoryDatabase(HelpThreads.HELP_THREADS);
        ChatGptService chatGptService = mock(ChatGptService.class);
        HelpSystemHelper failingHelper = new HelpSystemHelper(config, database, chatGptService,
                _ -> CompletableFuture.failedFuture(new IllegalStateException("boom")));

        MessageEmbed embed = failingHelper.generateGptResponseEmbed(ORIGINAL_ANSWER, selfUser,
                "example question", ChatGptModel.FAST);

        assertEquals(ORIGINAL_ANSWER, embed.getDescription());
    }
}
