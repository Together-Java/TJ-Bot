## Blacklisting a Bot Feature in the Configuration

If you need to blacklist a specific feature in your bot, follow these steps to exclude it from execution:

1. **Identify the Feature**:
    - Determine the full class name (including the package) of the feature you want to blacklist.
    - For example, let's assume the feature is named `ChatGptCommand.java`, located in the package `org.togetherjava.tjbot.features.chatgpt`.
    - The full class name would be `org.togetherjava.tjbot.features.chatgpt.ChatGptCommand`.

2. **Edit the Configuration File (`config.json`)**:
    - Open your bot's configuration file (`config.json`).
    - Locate the `"featureBlacklist"` section.

3. **Add the Feature to the Blacklist**:
    - Under `"normal"`, add the full class name of the feature you want to blacklist.
    - For example:

    ```json
    "featureBlacklist": {
        "normal": [
            "org.togetherjava.tjbot.features.chatgpt.ChatGptCommand"
        ],
        "special": []
    }
    ```

    - The `"normal"` section will prevent the specified feature from being executed when added via the `Features.java` file.

4. **Save and Apply Changes**:
    - Save the configuration file.
    - If your bot is running, restart it to apply the changes.

5. **Additional Note**:
    - The `"special"` section can be used for features that are not added via `Features.java` for any reason.
