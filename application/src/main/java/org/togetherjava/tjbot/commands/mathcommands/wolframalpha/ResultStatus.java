package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

public enum ResultStatus {
    SUCCESS,
    NOT_SUCCESS,
    ERROR;

    static ResultStatus getResultStatus(QueryResult result) {
        if (result.isSuccess()) {
            return SUCCESS;
        } else if (!result.isError()) {
            return NOT_SUCCESS;
        } else {
            return ERROR;
        }
    }
}
