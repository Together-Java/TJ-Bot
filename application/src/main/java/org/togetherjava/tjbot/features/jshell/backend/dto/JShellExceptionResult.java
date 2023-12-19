package org.togetherjava.tjbot.features.jshell.backend.dto;

/**
 * The thrown exception.
 * 
 * @param exceptionClass the class of the exception
 * @param exceptionMessage the message of the exception
 */
public record JShellExceptionResult(String exceptionClass, String exceptionMessage) {}
