package org.togetherjava.tjbot.features.moderation.scam;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.ScamBlockerConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ScamDetectorTest {
    private static final int SUSPICIOUS_ATTACHMENTS_THRESHOLD = 3;
    private static final String SUSPICIOUS_ATTACHMENT_NAME = "scam.png";

    private ScamDetector scamDetector;

    @BeforeEach
    void setUp() {
        Config config = mock(Config.class);
        ScamBlockerConfig scamConfig = mock(ScamBlockerConfig.class);
        when(config.getScamBlocker()).thenReturn(scamConfig);

        when(scamConfig.getSuspiciousKeywords()).thenReturn(Set.of("nitro", "boob", "sexy", "sexi",
                "esex", "steam", "gift", "onlyfans", "bitcoin", "btc", "promo", "trader", "trading",
                "whatsapp", "crypto", "^claim", "teen", "adobe", "hack", "steamcommunity",
                "freenitro", "^earn$", "^earning", ".exe$"));
        when(scamConfig.getHostWhitelist()).thenReturn(Set.of("discord.com", "discord.media",
                "discordapp.com", "discordapp.net", "discordstatus.com", "thehackernews.com",
                "gradle.org", "help.gradle.org", "youtube.com", "www.youtube.com"));
        when(scamConfig.getHostBlacklist()).thenReturn(Set.of("bit.ly", "discord.gg", "teletype.in",
                "t.me", "corematrix.us", "u.to", "steamcommunity.com", "goo.su", "telegra.ph",
                "shorturl.at", "cheatings.xyz", "transfer.sh", "tobimoller.space"));
        when(scamConfig.getSuspiciousHostKeywords()).thenReturn(Set.of("discord", "nitro",
                "premium", "free", "cheat", "crypto", "telegra", "telety"));
        when(scamConfig.getIsHostSimilarToKeywordDistanceThreshold()).thenReturn(2);
        when(scamConfig.getSuspiciousAttachmentsThreshold())
            .thenReturn(SUSPICIOUS_ATTACHMENTS_THRESHOLD);
        when(scamConfig.getSuspiciousAttachmentNamePattern())
            .thenReturn(SUSPICIOUS_ATTACHMENT_NAME);

        when(scamConfig.getTrustedUserRolePattern()).thenReturn("Moderator");

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

    @ParameterizedTest
    @MethodSource("provideRealFalsePositiveMessages")
    @DisplayName("Can ignore real false positive messages")
    void ignoresFalsePositives(String falsePositiveMessage) {
        // GIVEN a real false positive message
        // WHEN analyzing it
        boolean isScamResult = scamDetector.isScam(falsePositiveMessage);

        // THEN does not flag it as scam
        assertFalse(isScamResult);
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

    @Test
    @DisplayName("Messages containing multiple suspicious attachments are flagged as scam")
    void detectsSuspiciousAttachments() {
        // GIVEN an empty message containing suspicious attachments
        String content = "";
        Message.Attachment attachment = createImageAttachmentMock(SUSPICIOUS_ATTACHMENT_NAME);
        List<Message.Attachment> attachments =
                Collections.nCopies(SUSPICIOUS_ATTACHMENTS_THRESHOLD, attachment);
        Message message = createMessageMock(content, attachments);

        // WHEN analyzing it
        boolean isScamResult = scamDetector.isScam(message);

        // THEN flags it as scam
        assertTrue(isScamResult);
    }

    @Test
    @DisplayName("Messages containing text content are not flagged for suspicious attachments")
    void ignoresAttachmentsIfContentProvided() {
        // GIVEN a non-empty message containing suspicious attachments
        String content = "Hello World";
        Message.Attachment attachment = createImageAttachmentMock(SUSPICIOUS_ATTACHMENT_NAME);
        List<Message.Attachment> attachments =
                Collections.nCopies(SUSPICIOUS_ATTACHMENTS_THRESHOLD, attachment);
        Message message = createMessageMock(content, attachments);

        // WHEN analyzing it
        boolean isScamResult = scamDetector.isScam(message);

        // THEN flags it as harmless
        assertFalse(isScamResult);
    }

    @Test
    @DisplayName("Messages containing not enough suspicious attachments are not flagged")
    void ignoresIfNotEnoughSuspiciousAttachments() {
        // GIVEN an empty message containing some, but not enough suspicious attachments
        String content = "";

        Message.Attachment badAttachment = createImageAttachmentMock(SUSPICIOUS_ATTACHMENT_NAME);
        Message.Attachment goodAttachment = createImageAttachmentMock("good.png");
        int badAttachmentAmount = SUSPICIOUS_ATTACHMENTS_THRESHOLD - 1;
        List<Message.Attachment> attachments =
                new ArrayList<>(Collections.nCopies(badAttachmentAmount, badAttachment));
        attachments.add(goodAttachment);

        Message message = createMessageMock(content, attachments);

        // WHEN analyzing it
        boolean isScamResult = scamDetector.isScam(message);

        // THEN flags it as harmless
        assertFalse(isScamResult);
    }

    @Test
    @DisplayName("Messages containing harmless attachments are not flagged")
    void ignoresHarmlessAttachments() {
        // GIVEN an empty message containing only harmless attachments
        String content = "";
        Message.Attachment attachment = createImageAttachmentMock("good.png");
        List<Message.Attachment> attachments =
                Collections.nCopies(SUSPICIOUS_ATTACHMENTS_THRESHOLD, attachment);
        Message message = createMessageMock(content, attachments);

        // WHEN analyzing it
        boolean isScamResult = scamDetector.isScam(message);

        // THEN flags it as harmless
        assertFalse(isScamResult);
    }

    @Test
    @DisplayName("Suspicious messages send by trusted users are not flagged")
    void ignoreTrustedUser() {
        // GIVEN a scam message send by a trusted user
        String content = "Checkout https://bit.ly/3IhcLiO to get your free nitro !";
        Member trustedUser = createAuthorMock(List.of("Moderator"));
        Message message = createMessageMock(content, List.of());

        when(message.getMember()).thenReturn(trustedUser);

        // WHEN analyzing it
        boolean isScamResult = scamDetector.isScam(message);

        // THEN flags it as harmless
        assertTrue(isScamResult);
    }

    private static Message createMessageMock(String content, List<Message.Attachment> attachments) {
        Message message = mock(Message.class);
        when(message.getContentRaw()).thenReturn(content);
        when(message.getContentDisplay()).thenReturn(content);
        when(message.getAttachments()).thenReturn(attachments);
        return message;
    }

    private static Message.Attachment createImageAttachmentMock(String name) {
        Message.Attachment attachment = mock(Message.Attachment.class);
        when(attachment.isImage()).thenReturn(true);
        when(attachment.getFileName()).thenReturn(name);
        return attachment;
    }

    private static Member createAuthorMock(List<String> roleNames) {
        List<Role> roles = new ArrayList<>();
        for (String roleName : roleNames) {
            Role role = mock(Role.class);
            when(role.getName()).thenReturn(roleName);
            roles.add(role);
        }

        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(roles);
        return member;
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
                "Check this - https://transfer.sh/get/ajmkh3l7tzop/Setup.exe",
                """
                        Secrets of the crypto market that top traders don’t want you to know! I’m looking to help some individuals who
                        are serious about earning over $100K weekly in the market. Remember, I’ll require just 15% of your profits once
                        you start seeing earnings. Note: I’m only looking for serious and truly interested individuals.
                        Text me on TG/WhatApps for more info on how to get started +(123)123-1230 https://t.me/officialjohnsmith""",
                """
                        💻 Senior Full Stack Engineer | 8+ Years Experience with me
                        Hi, I’m a Senior Software Engineer with over 8 years of experience building scalable website, cloud-native software solutions across industries like healthcare, fintech, e-commerce, gaming, logistics, and energy.
                        🧰 Core Skills:
                        Frontend: React, Vue, Angular, Next.js, TypeScript, Web3 integration, Svelte, Three.js, Pixi.js
                        Backend: Node.js, NestJS, PHP (Laravel, Symfony), Python (FastAPI/Flask), .Net, Rails
                        Databases: MongoDB, MySQL, PostgreSQL, Redis
                        Ecommerce platforms: MedusaJS, MercurJS, Shopify (Gadget)
                        Automation & Bots: Token Swap / Trading Bots, AI/ML & Generative AI & CRM, Automation online sites
                        🔍 Notable Projects:
                        Property Shield: Scalable backend with NestJS, Redis Streams, MongoDB, Supabase
                        Ready Education: Frontend state architecture with NgRx, Next / Vue, TypeScript with Web3,
                        Kozoom Multimedia: Secure enterprise login using React, Redux, Azure
                        B2CWorkflow Builder (React Flow)
                        📂 Portfolio: https://tobimoller.space/
                        📬 Open to freelance gigs, contracts, and bounties — let’s talk!""",
                """
                        I'll help the first 10 people interested on how to start earning $100k or more within a week,
                        but you will reimburse me 10% of your profits when you receive it. Note: only interested people should
                        send a friend request or send me a dm! ask me (HOW) via Telegram username @JohnSmith_123""",
                """
                        Ready to unlock your earning potential in the digital market? you can start earning $100,000 and even more
                        as a beginner from the digital market, DM me for expert guidance or contact me directly on telegram and start building your financial future.
                        Telegram username @JohnSmith123""",
                "Grab it before it's deleted (available for Windows and macOS): https://www.reddit.com/r/TVBaFreeHub/comments/12345t/ninaatradercrackedfullpowertradingfreefor123/");
    }

    private static List<String> provideRealFalsePositiveMessages() {
        return List.of(
                """
                        https://learn.microsoft.com/en-us/dotnet/csharp/fundamentals/types/anonymous-types""",
                "And according to quick google search. Median wage is about $23k usd",
                """
                        $ docker image prune -a
                        WARNING! This will remove all images without at least one container associated to them.
                        Are you sure you want to continue? [y/N] y
                        ...
                        Total reclaimed space: 37.73GB""",
                """
                        Exception in thread "main" java.lang.NoSuchMethodError: 'java.lang.String org.junit.platform.engine.discovery.MethodSelector.getMethodParameterTypes()'
                        at com.intellij.junit5.JUnit5TestRunnerUtil.loadMethodByReflection(JUnit5TestRunnerUtil.java:127)
                        at com.intellij.junit5.JUnit5TestRunnerUtil.buildRequest(JUnit5TestRunnerUtil.java:102)
                        at com.intellij.junit5.JUnit5IdeaTestRunner.startRunnerWithArgs(JUnit5IdeaTestRunner.java:43)
                        at com.intellij.rt.junit.IdeaTestRunner$Repeater$1.execute(IdeaTestRunner.java:38)
                        at com.intellij.rt.execution.junit.TestsRepeater.repeat(TestsRepeater.java:11)
                        at com.intellij.rt.junit.IdeaTestRunner$Repeater.startRunnerWithArgs(IdeaTestRunner.java:35)
                        at com.intellij.rt.junit.JUnitStarter.prepareStreamsAndStart(JUnitStarter.java:232)
                        at com.intellij.rt.junit.JUnitStarter.main(JUnitStarter.java:55)""",
                """
                        The average wage here (not the median, which is lower) gives you a take-home of about $68k in New Zealand dollars.
                        The median house-price in my city (which is not at all the most expensive city) is ~$740k.
                        That's an 11 year save for an average earner for an average house without spending anything.""",
                "https://thehackernews.com/2025/07/alert-exposed-jdwp-interfaces-lead-to.html",
                """
                        ~/Developer/TJ-Bot develop ❯ ./gradlew build 10:20:05 PM
                        FAILURE: Build failed with an exception.
                        What went wrong:
                        class name.remal.gradleplugins.sonarlint.SonarLintPlugin
                        tried to access private field org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin.extension
                        (name.remal.gradleplugins.sonarlint.SonarLintPlugin is in unnamed module of loader
                        org.gradle.internal.classloader.VisitableURLClassLoader$InstrumentingVisitableURLClassLoader @55f4c79b;
                        org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin is in unnamed module of
                        loader org.gradle.initialization.MixInLegacyTypesClassLoader @49b2a47d)
                        Try:
                        Run with --stacktrace option to get the stack trace.
                        Run with --info or --debug option to get more log output.
                        Run with --scan to get full insights.
                        Get more help at https://help.gradle.org/.
                        BUILD FAILED in 795ms
                        7 actionable tasks: 7 up-to-date
                        ~/Developer/TJ-Bot develop ❯""",
                """
                        For example. I enter 3.45 for the price and 3 for the count. It results in 10.350000000000001 for some reason. I followed Bro Code's video:
                        https://www.youtube.com/watch?v=P8CVPIaRmys&list=PLZPZq0rRZOOjNOZYq_R2PECIMglLemc&index=6
                        and his does not do this. Why is this?
                        import java.util.Scanner;
                        public class ShoppingCart {
                          public static void main(String[] args){
                            // Shopping Cart Arithmetic Practice
                            Scanner input = new Scanner(System.in);
                            String item;
                            double price;
                            int count;
                            char currency = '$';
                            double total;
                            System.out.print("What item would you like to buy?: ");
                            item = input.nextLine();
                            System.out.print("What is the price of this item?: ");
                            price = input.nextDouble();
                            System.out.print("How many " + item + "(s) would you like to buy?: ");
                            count = input.nextInt();
                            total = price * count;
                            System.out.println("\\nYou bought " + count + " " + item + "(s).\\n");
                            System.out.println("Your total is " + currency + total);
                          }
                        }""",
                "@squidxtv https://cdn.steamusercontent.com/ugc/12827361819537692968/A7B3AC5A176E7B2287B5E84B9A0BE9754F5A6388/",
                """
                        today i understood, why security is joke, even for people on top
                        https://micahsmith.com/ddosecrets-publishes-410-gb-of-heap-dumps-hacked-from-telemessages-archive-server/""",
                """
                        Hey guys @everyone, apologise for disturbing,
                        I wanted to ask what's the scope of Java in future like after 2030 in USA, like the newer frameworks will
                        replace Spring Boot ... and how AI will play it role ...
                        I am very much confused, what to do, I tired exploring Machine Learning, but I don't know why it felt more
                        like a burden then enjoyment, but spring boot was fun, although exploring microservice architecture
                        is was tricky mostly when it came to deployment and it become really confusing...""",
                "https://www.cloudflare.com/learning/email-security/dmarc-dkim-spf/",
                """
                        It was pretty pricey, and the costs likely differ a lot from country to country
                        (keeping in mind that a portion is importing of equipment to NZ and some is labour in a very different market).
                        We have 13.5KW of storage, a 10KW inverter, 11.5KW of generation and an EV charger.
                        All up, on a 1% 'green loan', it was $40k NZD (~$23k USD)""");
    }
}
