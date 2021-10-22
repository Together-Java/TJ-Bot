package org.togetherjava.tjbot.util;

/**
 * Utility class holding various markdown templates to be used in conjunction with String#format()
 */
public final class MessageTemplate {
    private MessageTemplate() {}

    public static final String PLAINTEXT_MESSAGE_TEMPLATE = "```\n%s\n```";
    public static final String JAVA_MESSAGE_TEMPLATE = "```java\n%s\n```";
    public static final String KOTLIN_MESSAGE_TEMPLATE = "```kotlin\n%s\n```";
    public static final String SQL_MESSAGE_TEMPLATE = "```sql\n%s\n```";
    public static final String SHELL_MESSAGE_TEMPLATE = "```shell\n%s\n```";
    public static final String JAVASCRIPT_MESSAGE_TEMPLATE = "```javascript\n%s\n```";
    public static final String TAGLIST_MESSAGE_TEMPLATE =
            "All available tags:\n" + PLAINTEXT_MESSAGE_TEMPLATE;
}
