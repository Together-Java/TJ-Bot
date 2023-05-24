package org.togetherjava.tjbot.features.chatgpt;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.chaptgpt.ChatGptService;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatGptServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(ChatGptServiceTest.class);
    private Config config;
    private ChatGptService chatGptService;

    @BeforeEach
    void setUp() {
        config = mock();
        String openaiKey = System.getenv("openai-key");
        when(config.getOpenaiApiKey()).thenReturn(openaiKey);
        chatGptService = new ChatGptService(config);
    }

    @Test
    void askToGenerateLongPoem() {
        Optional<String> response = chatGptService.ask("generate a long poem");
        response.ifPresent(logger::warn);
        response.ifPresent(this::testResponseLength);
    }

    @Test
    void askHowToSetupJacksonLibraryWithExamples() {
        Optional<String> response =
                chatGptService.ask("How to setup jackson library with examples");
        response.ifPresent(logger::warn);
        response.ifPresent(this::testResponseLength);
    }

    @Test
    void askDockerReverseProxyWithNginxGuide() {
        Optional<String> response = chatGptService.ask("Docker reverse proxy with nginx guide");
        response.ifPresent(logger::warn);
        response.ifPresent(this::testResponseLength);
    }

    @Test
    void askWhatIsWrongWithMyCode() {
        Optional<String> response = chatGptService.ask("""
                    What is wrong with my code?

                    package Lab13;
                    import java.lang.reflect.Array;
                    import java.util.*;
                    import java.io.*;


                    public class RandomDoubleFile {

                        Random r = new Random();
                        //Read File


                        public void createDouble() {

                            try {
                                //Create/Open/Write File
                                FileOutputStream f = new FileOutputStream("double.dat");
                                BufferedOutputStream b = new BufferedOutputStream(f);
                                DataOutputStream d = new DataOutputStream(b);

                                //Write Number of Doubles
                                int myint = r.nextInt(50);
                                d.write(myint);

                                //Write Doubles
                                for (int i = 0; i < myint ; i++) {
                                    d.writeDouble(r.nextDouble());
                                }
                                d.close();
                            }catch (IOException e) {
                                System.out.println(e.getMessage());
                            }

                        }
                        public void readDouble() {

                            double[] doublesArray = new double[0];

                            try {
                                //Configure Read Streams
                                FileInputStream f = new FileInputStream("double.dat");
                                BufferedInputStream b = new BufferedInputStream(f);
                                DataInputStream d = new DataInputStream(b);

                                //Read Num of Doubles
                                int numberOfDoubles = d.readInt();
                                doublesArray = new double[numberOfDoubles];

                                //Try-Catch End-of-file
                                try {
                                    //Store Doubles in List
                                    for (int i = 0; ; i++) {
                                        doublesArray[i] = d.readDouble();
                                    }

                                } catch (EOFException eof) {
                                    d.close();
                                }
                            }catch (IOException e) {
                                System.out.println(e.getMessage());
                            }

                            //ReadList
                            for(int i=0;i < Array.getLength(doublesArray); i++){
                            System.out.println(doublesArray[i]);
                            }
                        }

                            public static void main(String[] args){
                                RandomDoubleFile rdb = new RandomDoubleFile();
                                //rdb.createDouble();
                                rdb.readDouble();
                            }
                    }
                """);
        response.ifPresent(logger::warn);
        response.ifPresent(this::testResponseLength);
    }

    @Test
    void askWhyDoesItTakeYouMoreThan10SecondsToAnswer() {
        Optional<String> response =
                chatGptService.ask("Why does it take you more than 10 seconds to answer");
        response.ifPresent(logger::warn);
        response.ifPresent(this::testResponseLength);
    }

    private void testResponseLength(String response) {
        int AI_RESPONSE_CHARACTER_LIMIT = 2000;
        Assertions.assertTrue(response.length() < AI_RESPONSE_CHARACTER_LIMIT,
                "Response length is NOT within character limit: " + response.length());
        logger.warn("Response length was: {}", response.length());
    }
}
