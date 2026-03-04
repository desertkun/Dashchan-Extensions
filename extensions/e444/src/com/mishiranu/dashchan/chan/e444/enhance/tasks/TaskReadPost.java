package com.mishiranu.dashchan.chan.e444.enhance.tasks;

import chan.content.model.Post;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444ChanPerformer;
import com.mishiranu.dashchan.chan.e444.E444RequestPerformer;
import com.mishiranu.dashchan.chan.e444.E444JsonUtils;
import com.mishiranu.dashchan.chan.e444.E444Model;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceTask;
import java.io.IOException;

public final class TaskReadPost implements EnhanceTask<Post> {
    private final String boardName;
    private final int postNumber;

    public TaskReadPost(String boardName, int postNumber) {
        this.boardName = boardName;
        this.postNumber = postNumber;
    }

    @Override
    public Post run(E444ChanLocator locator, HttpRequest.Preset preset) throws Exception {
        E444Model.MobilePostResponse response = E444RequestPerformer.request(
                        preset, "api", "mobile", "v2", "post", boardName, Integer.toString(postNumber))
                .param("_", Long.toString(System.currentTimeMillis()))
                .configure(HttpRequest::setGetMethod)
                .performJson(E444Model.MobilePostResponse.class);
        return E444ChanPerformer.createPost(response.post, locator);
    }
}
