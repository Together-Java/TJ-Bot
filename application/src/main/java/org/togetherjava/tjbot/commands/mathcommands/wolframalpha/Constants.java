package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageUpdateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.image.ImageObserver;
import java.net.http.HttpClient;
import java.util.Map;

public enum Constants {
    ;

    /**
     * Starting part of a regular wolframalpha query link.
     */
    public static final String USER_ENDPOINT = "https://www.wolframalpha.com/input";
    static final Logger logger = LoggerFactory.getLogger(WolframAlphaCommand.class);
    /**
     * Maximum Embeds that can be sent in a {@link WebhookMessageUpdateAction}
     */
    static final int MAX_EMBEDS = 10;
    /**
     * The image observer used for getting data from images.
     */
    static final ImageObserver IMAGE_OBSERVER = null;
    /**
     * The width of the margins of the images generated
     */
    static final int IMAGE_MARGIN_WIDTH = 10;
    /**
     * The height of the margins of the images generated
     */
    public static final int IMAGE_MARGIN_HEIGHT = 15;

    static final XmlMapper XML = new XmlMapper();
    static final int MAX_IMAGE_HEIGHT_PX = 400;
    /**
     * WolframAlpha text Color
     */
    static final Color WOLFRAM_ALPHA_TEXT_COLOR = Color.decode("#3C3C3C");
    /**
     * WolframAlpha Font
     */
    static final Font WOLFRAM_ALPHA_FONT = new Font("Times", Font.PLAIN, 15)
        .deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON));
    /**
     * Height of the unscaled text displayed in Font {@link #WOLFRAM_ALPHA_FONT}
     */
    static final int TEXT_HEIGHT = 20;
    static final int HTTP_STATUS_CODE_OK = 200;
    static final String QUERY_OPTION = "query";
    /**
     * WolframAlpha API endpoint to connect to.
     *
     * @see <a href=
     *      "https://products.wolframalpha.com/docs/WolframAlpha-API-Reference.pdf">WolframAlpha API
     *      Reference</a>.
     */
    static final String API_ENDPOINT = "http://api.wolframalpha.com/v2/query";
    static final HttpClient client = HttpClient.newHttpClient();
}
