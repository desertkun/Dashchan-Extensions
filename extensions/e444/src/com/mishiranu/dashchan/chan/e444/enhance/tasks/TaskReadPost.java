package com.mishiranu.dashchan.chan.e444.enhance.tasks;

import chan.content.model.Post;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.text.JsonSerial;
import chan.text.ParseException;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444IpRequestPerformer;
import com.mishiranu.dashchan.chan.e444.E444ModelMapper;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceTask;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public final class TaskReadPost implements EnhanceTask<Post> {
    private final String boardName;
    private final int postNumber;

    public TaskReadPost(String boardName, int postNumber) {
        this.boardName = boardName;
        this.postNumber = postNumber;
    }

    @Override
    public Post run(E444ChanLocator locator, HttpRequest.Preset preset) throws Exception {
        HttpResponse response = E444IpRequestPerformer.request(
                        preset, "api", "mobile", "v2", "post", boardName, Integer.toString(postNumber))
                .param("_", Long.toString(System.currentTimeMillis()))
                .configure(HttpRequest::setGetMethod)
                .perform();
        try {
            String responseBody = response.readString();
            Object root = new JSONTokener(responseBody).nextValue();
            JSONObject postObject = findPostObject(root, postNumber);
            if (postObject == null) {
                throw new HttpException(0, "Post object not found in single post response");
            }
            try (InputStream input =
                            new ByteArrayInputStream(postObject.toString().getBytes(StandardCharsets.UTF_8));
                    JsonSerial.Reader reader = JsonSerial.reader(input)) {
                Post post = E444ModelMapper.createPost(reader, locator, null, boardName);
                if (post == null) {
                    throw new HttpException(0, "Empty post response");
                }
                return post;
            }
        } catch (JSONException e) {
            throw new HttpException(0, "Invalid single post JSON");
        } catch (ParseException e) {
            throw new HttpException(0, "Invalid single post response");
        } catch (IOException e) {
            throw response.fail(e);
        }
    }

    private static JSONObject findPostObject(Object value, int targetPostNumber) {
        JSONObject[] fallback = new JSONObject[1];
        JSONObject exact = findPostObjectRecursive(value, targetPostNumber, fallback);
        return exact != null ? exact : fallback[0];
    }

    private static JSONObject findPostObjectRecursive(Object value, int targetPostNumber, JSONObject[] fallback) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.has("num")) {
                int parsedPostNumber = parsePostNumber(object.opt("num"));
                if (parsedPostNumber == targetPostNumber) {
                    return object;
                }
                if (fallback[0] == null) {
                    fallback[0] = object;
                }
            }
            for (String key : new String[] {"post", "data", "result", "thread", "op", "posts", "threads"}) {
                if (!object.has(key)) {
                    continue;
                }
                JSONObject found = findPostObjectRecursive(object.opt(key), targetPostNumber, fallback);
                if (found != null) {
                    return found;
                }
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject found = findPostObjectRecursive(object.opt(key), targetPostNumber, fallback);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                JSONObject found = findPostObjectRecursive(array.opt(i), targetPostNumber, fallback);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int parsePostNumber(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
