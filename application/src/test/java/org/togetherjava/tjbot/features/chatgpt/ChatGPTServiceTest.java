package org.togetherjava.tjbot.features.chatgpt;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A utility class that allows communication to ChatGPT to get response based on inputs. Used to
 * generate test cases for the AIResponseParserTest class. Use the JUnit annotation @Test to quickly
 * generate a response. Remove the annotation before testing whole project.
 */
class ChatGPTServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(ChatGPTServiceTest.class);
    private Config config;
    private ChatGptService chatGptService;

    @BeforeEach
    void setUp() {
        config = mock();
        String openaiKey = System.getenv("openai-key");
        when(config.getOpenaiApiKey()).thenReturn(openaiKey);
        chatGptService = new ChatGptService(config);
    }

    void askToGenerateLongPoem() {
        Optional<String[]> response = chatGptService.ask("generate a long poem");
        response.ifPresent(this::toLog);
        response.ifPresent(this::testResponseLength);
    }

    void askHowToSetupJacksonLibraryWithExamples() {
        Optional<String[]> response =
                chatGptService.ask("How to setup jackson library with examples");
        response.ifPresent(this::toLog);
        response.ifPresent(this::testResponseLength);
    }

    void askDockerReverseProxyWithNginxGuide() {
        Optional<String[]> response = chatGptService.ask("Docker reverse proxy with nginx guide");
        response.ifPresent(this::toLog);
        response.ifPresent(this::testResponseLength);
    }

    void askWhatIsWrongWithMyCode() {
        Optional<String[]> response = chatGptService.ask("""
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
        response.ifPresent(this::toLog);
        response.ifPresent(this::testResponseLength);
    }

    @Test
    void howToDoTaskUsingSwing() {
        Optional<String[]> response = chatGptService.ask(
                "Swing What is the best way to code so that I can have multiple panels that change depending on the selected drop down list option?");
        response.ifPresent(this::toLog);
        response.ifPresent(this::testResponseLength);
    }

    void askWhyDoesItTakeYouMoreThan10SecondsToAnswer() {
        Optional<String[]> response =
                chatGptService.ask("Why does it take you more than 10 seconds to answer");
        response.ifPresent(this::toLog);
        response.ifPresent(this::testResponseLength);
    }

    private void testResponseLength(String[] responses) {
        int AI_RESPONSE_CHARACTER_LIMIT = 2000;
        for (String response : responses) {
            Assertions.assertTrue(response.length() <= AI_RESPONSE_CHARACTER_LIMIT,
                    "Response length is NOT within character limit: " + response.length());
            logger.warn("Response length was: {}", response.length());
        }
    }

    private void toLog(String[] responses) {
        for (String response : responses) {
            logger.warn(response);
        }
    }
}
