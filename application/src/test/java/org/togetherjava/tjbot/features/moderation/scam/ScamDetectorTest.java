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
            .thenReturn(Set.of("nitro", "boob", "sexy", "sexi", "esex", "steam", "gift", "onlyfans",
                    "bitcoin", "btc", "promo", "trader", "trading", "whatsapp", "crypto", "claim",
                    "teen", "adobe", "hack", "steamcommunity", "freenitro", "usd", "earn", ".exe"));
        when(scamConfig.getHostWhitelist()).thenReturn(Set.of("discord.com", "discord.media",
                "discordapp.com", "discordapp.net", "discordstatus.com"));
        when(scamConfig.getHostBlacklist()).thenReturn(Set.of("bit.ly", "discord.gg", "teletype.in",
                "t.me", "corematrix.us", "u.to", "steamcommunity.com", "goo.su", "telegra.ph",
                "shorturl.at", "cheatings.xyz", "transfer.sh"));
        when(scamConfig.getSuspiciousHostKeywords())
            .thenReturn(Set.of("discord", "nitro", "premium", "free", "cheat", "crypto", "tele"));
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
                "@everyone Join Now | Free All 12-18 y.o. https://discord.gg/eesexe",
                "@everyone steam gift 50$ - steamcommunity.com/gift-card/pay/51", """
                                @everyone
                                20$ from steam
                                steamcommunity.com/gift/001023
                        """, """
                                Free Onlyfans leaks and 18+ Content:
                                https://discord.gg/4ue9esAuR7 🍑 @everyone
                        """, """
                                Bro, don't miss it:
                                https://teletype.in/@game_insider/valve_promobo
                        """,
                "Stake gives a 1500$! When you register using the promo code \"em4fh4bj67\" - Stake.com/auth/register?promo=em4fh4bj67",
                """
                                @everyone
                                50$ from steam
                                steamcommunity.com/gift/81237
                        """,
                """
                                2025📊The best repacks of 24/7 only in our group📊
                                IPTVPlayet AtlasVPN TradingView-Premium== Become a real trader with the best offers...
                                ✍️ https://t.me/repackMEMIaa ✍️
                        """,
                """
                                Check there is btc cost 109k
                                https://corematrix.us/eni
                        """,
                """
                                I’ll teach 10 people to earn $30k or more within 72 hours but you will pay me 10% of your profit when you receive it. Note only interest people should apply, drop a message let's get started by asking (HOW)
                                https://t.me/Christopher_Schwingo
                                WhatsApp +1 (571) 598-2749
                        """,
                """
                                I’ll teach 10 interested people on how to earn $30k or more within 24 hours from the crypto market, but you’ll promise to pay me 20% of the profit. Note only interested people should contact me (HOW) let get started
                                👇👇👇
                                https://t.me/Official_Nichola1o
                        """,
                """
                                Bro steam gift 50$ - steamcommunity.com/gift-card/pay/51
                                @here @everyone
                        """, """
                                Bro gift 50$ - steamcommunity.com/gift-card/pay/51
                                @everyone
                        """, """
                                50$
                                steamcommunity.com/gift/0a9zk2j1man2o
                        """, """
                                Hello, i found good server🔞
                                https://discord.gg/e-womanso
                                @everyone
                        """, """
                                Gift 50$ - steamcommunity.com/gift-card/51
                                @everyone @here
                        """, """
                                50$ From steam
                                steamcommunity.com/gift/7656685834763978
                                @everyone
                        """, "catch 25$ https://u.to/ExatIO", """
                                STEAM 50$ GIFT, CLAIM NOW
                                steamcommunity.com/1249349825824
                                @everyone
                        """,
                "Hot Teen & Onlyfans Leaks :underage: :peach: https://discord.gg/nudesxxxo @everyone",
                """
                                Adobe Full Espanol GRATiS 2024
                                https://telegra.ph/Adobe-GRATIS-2024-FULL-ESPANOL-02-28
                                Tutorial video
                                https://www.youtube.com/watch?v=gmEZLz5xbmp
                                @everyone
                        """,
                "Get a $50 gift to your steam account balance https://shorturl.at/cpt09 Immerse yourself in the spring atmosphere now!",
                "#1 Free Hacks: https://cheatings.xyz/abc",
                """
                            I’ll help 10 people to earn 55k USDT within 72 hours but you will pay me 10% of your profit when you receive it. Note only interested people should apply, drop a message let’s get started by asking (How)
                            Or via TG: https://t.me/Charlie_Adamo
                        """,
                "Urgently looking for mods & collab managers https://discord.gg/cryptohireo",
                "Check this - https://transfer.sh/get/ajmkh3l7tzop/Setup.exe");
    }
}
