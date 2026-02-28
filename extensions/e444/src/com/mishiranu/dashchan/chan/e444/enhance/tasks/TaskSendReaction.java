package com.mishiranu.dashchan.chan.e444.enhance.tasks;

import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.util.StringUtils;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444IpRequestPerformer;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceTask;
import org.json.JSONException;
import org.json.JSONObject;

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
        HttpResponse response = E444IpRequestPerformer.request(preset, "api", "react")
                .param("board", boardName)
                .param("num", Integer.toString(postNumber))
                .param("icon", iconName)
                .configure(HttpRequest::setGetMethod)
                .perform();
        try {
            JSONObject responseJson = new JSONObject(response.readString());
            JSONObject errorObject = responseJson.optJSONObject("error");
            int errorCode = errorObject != null ? Math.abs(errorObject.optInt("code", Integer.MAX_VALUE)) : 0;
            if (errorCode != 0) {
                String errorMessage =
                        errorObject != null ? StringUtils.nullIfEmpty(errorObject.optString("message")) : null;
                throw new HttpException(0, errorMessage != null ? errorMessage : "Reaction error " + errorCode);
            }
        } catch (JSONException e) {
            throw new HttpException(0, "Invalid reaction response");
        }
        return null;
    }
}
