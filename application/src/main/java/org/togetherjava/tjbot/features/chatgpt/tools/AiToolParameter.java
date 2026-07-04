package org.togetherjava.tjbot.features.chatgpt.tools;

/**
 * Declares a single argument a {@link AiTool} accepts. Used to build the JSON schema sent to OpenAI
 * so the model knows which arguments to produce on a tool call.
 *
 * @param name argument name; must match the key the model returns in its JSON arguments object
 * @param description human-readable description shown to the model so it can decide when and how to
 *        fill the argument
 * @param type the JSON schema type the model should produce
 * @param required {@code true} if the model must always supply this argument; {@code false} marks
 *        it as optional
 */
public record AiToolParameter(String name, String description, AiToolParameterType type,
        boolean required) {
}
