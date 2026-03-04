package com.mishiranu.dashchan.chan.e444.enhance.tasks;

import chan.http.HttpException;
import chan.http.HttpRequest;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444RequestPerformer;
import com.mishiranu.dashchan.chan.e444.E444Model;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceTask;

public final class TaskSendReaction implements EnhanceTask<Void> {
    private final String boardName;
    private final int postNumber;
    private final String iconName;

    public TaskSendReaction(String boardName, int postNumber, String iconName) {
        this.boardName = boardName;
        this.postNumber = postNumber;
        this.iconName = iconName;
    }

    @Override
    public Void run(E444ChanLocator locator, HttpRequest.Preset preset) throws Exception {
        E444Model.ApiResponse response = E444RequestPerformer.request(preset, "api", "react")
            .param("board", boardName)
            .param("num", Integer.toString(postNumber))
            .param("icon", iconName)
            .configure(HttpRequest::setGetMethod)
            .performJson(E444Model.ApiResponse.class);
        if (response.error.code != 0) {
            throw new HttpException(0, response.error.message);
        }
        return null;
    }
}
