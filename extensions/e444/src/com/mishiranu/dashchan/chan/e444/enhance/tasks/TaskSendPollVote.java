package com.mishiranu.dashchan.chan.e444.enhance.tasks;

import chan.http.HttpException;
import chan.http.HttpRequest;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444Model;
import com.mishiranu.dashchan.chan.e444.E444RequestPerformer;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceTask;

public final class TaskSendPollVote implements EnhanceTask<Void> {
    private final String boardName;
    private final int postNumber;
    private final int voteIndex;

    public TaskSendPollVote(String boardName, int postNumber, int voteIndex) {
        this.boardName = boardName;
        this.postNumber = postNumber;
        this.voteIndex = voteIndex;
    }

    @Override
    public Void run(E444ChanLocator locator, HttpRequest.Preset preset) throws Exception {
        E444Model.ApiResponse response = E444RequestPerformer.request(preset, "api", "polls", "vote")
                .param("board", boardName)
                .param("num", Integer.toString(postNumber))
                .param("vote", Integer.toString(voteIndex))
                .configure(HttpRequest::setGetMethod)
                .performJson(E444Model.ApiResponse.class);
        if (response.result != 1) {
            throw new HttpException(0, response.error.message);
        }
        return null;
    }
}
