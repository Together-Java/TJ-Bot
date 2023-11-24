package org.togetherjava.tjbot.features.moderation.scam;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.ScamBlockerConfig;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ScamDetectorTest {
    private ScamDetector scamDetector;

    @BeforeEach
    void setUp() {
        Config config = mock(Config.class);
        ScamBlockerConfig scamConfig = mock(ScamBlockerConfig.class);
        when(config.getScamBlocker()).thenReturn(scamConfig);

        when(scamConfig.getSuspiciousKeywords())
            .thenReturn(Set.of("nitro", "boob", "sexi", "esex"));
        when(scamConfig.getHostWhitelist()).thenReturn(Set.of("discord.com", "discord.gg",
                "discord.media", "discordapp.com", "discordapp.net", "discordstatus.com"));
        when(scamConfig.getHostBlacklist()).thenReturn(Set.of("bit.ly"));
        when(scamConfig.getSuspiciousHostKeywords())
            .thenReturn(Set.of("discord", "nitro", "premium"));
        when(scamConfig.getIsHostSimilarToKeywordDistanceThreshold()).thenReturn(2);

        scamDetector = new ScamDetector(config);
    }

    @ParameterizedTest
    @MethodSource("provideRealScamMessages")
    @DisplayName("Can detect real scam messages")
    void detectsRealScam(String scamMessage) {
        // GIVEN a real scam message
        // WHEN analyzing it
        boolean isScamResult = scamDetector.isScam(scamMessage);

        // THEN flags it as scam
        assertTrue(isScamResult);
    }

    @Test
    @DisplayName("Can detect messages that contain blacklisted websites as scam")
    void detectsBlacklistedWebsite() {
        // GIVEN a message with a link to a blacklisted website
        String scamMessage = "Checkout https://bit.ly/3IhcLiO to get your free nitro !";

        // WHEN analyzing it
        boolean isScamResult = scamDetector.isScam(scamMessage);

        // THEN flags it as scam
        assertTrue(isScamResult);
    }

    @Test
    @DisplayName("Can detect messages that contain whitelisted websites and does not flag them as scam")
    void detectsWhitelistedWebsite() {
        // GIVEN a message with a link to a whitelisted website
        String harmlessMessage =
                "Checkout https://discord.com/nitro to get your nitro - but not for free.";

        // WHEN analyzing it
        boolean isScamResult = scamDetector.isScam(harmlessMessage);

        // THEN flags it as harmless
        assertFalse(isScamResult);
    }

    @Test
    @DisplayName("Can detect messages that contain links to suspicious websites and flags them as scam")
    void detectsSuspiciousWebsites() {
        // GIVEN a message with a link to a suspicious website
        String scamMessage = "Checkout https://disc0rdS.com/n1tro to get your nitro for free.";

        // WHEN analyzing it
        boolean isScamResult = scamDetector.isScam(scamMessage);

        // THEN flags it as scam
        assertTrue(isScamResult);
    }

    @Test
    @DisplayName("Messages that contain links to websites that are not similar enough to suspicious keywords are not flagged as scam")
    void websitesWithTooManyDifferencesAreNotSuspicious() {
        // GIVEN a message with a link to a website that is not similar enough to a suspicious
        // keyword
        String notSimilarEnoughMessage =
                "Checkout https://dI5c0ndS.com/n1rt0 to get your nitro for free.";

        // WHEN analyzing it
        boolean isScamResult = scamDetector.isScam(notSimilarEnoughMessage);

        // THEN flags it as harmless
        assertFalse(isScamResult);
    }

    private static List<String> provideRealScamMessages() {
        return List.of("""
                🤩bro steam gived nitro - https://nitro-ds.online/LfgUfMzqYyx12""",
                """
                        @everyone, Free subscription for 3 months DISCORD NITRO - https://e-giftpremium.com/x12""",
                """
                        @everyone
                        Discord Nitro distribution from STEAM.
                        Get 3 month of Discord Nitro. Offer ends January 28, 2022 at 11am EDT. Customize your profile, share your screen in HD, update your emoji and more!
                        https://dlscrod-game.ru/promotionx12""",
                """
                        @everyone
                        Gifts for the new year, nitro for 3 months: https://discofdapp.com/newyearsx12""",
                """
                        @everyone yo , I got some nitro left over here https://steelsseriesnitros.com/billing/promotions/vh98rpaEJZnha5x37agpmOz3x12""",
                """
                        @everyone
                        :video_game: • Get Discord Nitro for Free from Steam Store
                        Free 3 months Discord Nitro
                        :clock630: • Personalize your profile, screen share in HD, upgrade your emojis, and more.
                        :gem: • Click to get Nitro: https://discoord-nittro.com/welcomex12
                        :Works only with prime go or rust or pubg""",
                """
                        @everyone, Check this lol, there nitro is handed out for free, take it until everything is sorted out https://dicsord-present.ru/airdropx12""",
                """
                        @everyone
                        • Get Discord Nitro for Free from Steam Store
                        Free 3 months Discord Nitro
                        • The offer is valid until at 6:00PM on November 30, 2021. Personalize your profile, screen share in HD, upgrade your emojis, and more.
                        • Click to get Nitro: https://dliscord.shop/welcomex12""",
                """
                        airdrop discord nitro by steam, take it https://bit.ly/30RzoKx""",
                """
                        Steam is giving away free discord nitro, have time to pick up at my link https://bit.ly/3nlzmUa before the action is over.""",
                """
                        @everyone, take nitro faster, it's already running out
                        https://discordu.gift/u1CHEX2sjpDuR3T5""",
                "@everyone join now https://discord.gg/boobise",
                "@everyone join now https://discord.gg/esexiest",
                "@everyone Join Now | Free All 12-18 y.o. https://discord.gg/eesexe");
    }
}
