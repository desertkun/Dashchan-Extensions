package com.mishiranu.dashchan.chan.e444;

import android.net.Uri;
import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.MultipartEntity;
import chan.text.JsonSerial;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.chan.e444.captcha.E444CaptchaReader;
import com.mishiranu.dashchan.chan.e444.captcha.E444CaptchaSender;
import com.mishiranu.dashchan.chan.e444.captcha.E444CaptchaSession;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceShim;
import com.mishiranu.dashchan.chan.e444.enhance.widgets.WidgetReaction;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

public class E444ChanPerformer extends ChanPerformer {
    private static final String[] PREFERRED_BOARDS_ORDER = {"Разное", "Политика", "Взрослым"};

    public E444ChanPerformer() {
        EnhanceShim.ensureActivityHookInstalled();
    }

    private static void parseBoardAndApplyReactionIcons(
            JsonSerial.Reader reader, String boardName, E444ModelMapper.BoardConfiguration boardConfiguration)
            throws IOException, ParseException {
        ArrayList<String> reactions = null;
        boolean reactionsEnabled = true;
        reader.startObject();
        while (!reader.endStruct()) {
            String boardFieldName = reader.nextName();
            switch (boardFieldName) {
                case "reactions": {
                    reactions = new ArrayList<>();
                    reader.startArray();
                    while (!reader.endStruct()) {
                        reactions.add(reader.nextString());
                    }
                    break;
                }
                case "enable_reactions": {
                    reactionsEnabled = reader.nextInt() != 0;
                    if (!reactionsEnabled) {
                        reactions = null;
                    }
                    break;
                }
                case "name": {
                    boardConfiguration.title = reader.nextString();
                    break;
                }
                case "info": {
                    boardConfiguration.description = reader.nextString();
                    break;
                }
                default: {
                    if (!boardConfiguration.handle(reader, boardFieldName)) {
                        reader.skip();
                    }
                    break;
                }
            }
        }
        WidgetReaction.setBoardReactionIcons(boardName, reactionsEnabled ? reactions : null);
    }

    @Override
    public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
        E444ChanLocator locator = E444ChanLocator.get(this);
        HttpResponse response = E444IpRequestPerformer.request(
                        data,
                        data.boardName,
                        (data.isCatalog() ? "catalog" : data.pageNumber == 0 ? "index" : data.pageNumber) + ".json")
                .configure(request -> request.setValidator(data.validator))
                .perform();
        E444ChanConfiguration configuration = E444ChanConfiguration.get(this);
        E444ModelMapper.BoardConfiguration boardConfiguration = new E444ModelMapper.BoardConfiguration();
        ArrayList<Posts> threads = new ArrayList<>();
        int boardSpeed = 0;
        try (InputStream input = response.open();
                JsonSerial.Reader reader = JsonSerial.reader(input)) {
            reader.startObject();
            while (!reader.endStruct()) {
                String name = reader.nextName();
                if (!boardConfiguration.handle(reader, name)) {
                    switch (name) {
                        case "threads": {
                            reader.startArray();
                            while (!reader.endStruct()) {
                                threads.add(E444ModelMapper.createThread(reader, locator, data.boardName));
                            }
                            break;
                        }
                        case "board_speed": {
                            boardSpeed = reader.nextInt();
                            break;
                        }
                        case "board": {
                            parseBoardAndApplyReactionIcons(reader, data.boardName, boardConfiguration);
                            break;
                        }
                        default: {
                            reader.skip();
                            break;
                        }
                    }
                }
            }
            configuration.updateFromThreadsPostsJson(data.boardName, boardConfiguration);
            return new ReadThreadsResult(threads).setBoardSpeed(boardSpeed);
        } catch (ParseException e) {
            throw new InvalidResponseException(e);
        } catch (IOException e) {
            throw response.fail(e);
        }
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    @Override
    public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
        E444ChanLocator locator = E444ChanLocator.get(this);
        E444ChanConfiguration configuration = E444ChanConfiguration.get(this);
        HttpResponse response = E444IpRequestPerformer.request(data, data.boardName, "res", data.threadNumber + ".json")
                .configure(request -> request.setValidator(data.validator))
                .perform();
        try (InputStream input = response.open();
                JsonSerial.Reader reader = JsonSerial.reader(input)) {
            E444ModelMapper.BoardConfiguration boardConfiguration = new E444ModelMapper.BoardConfiguration();
            ArrayList<Post> posts = null;
            int uniquePosters = 0;
            reader.startObject();
            while (!reader.endStruct()) {
                String name = reader.nextName();
                if (!boardConfiguration.handle(reader, name)) {
                    switch (name) {
                        case "threads": {
                            reader.startArray();
                            reader.startObject();
                            while (!reader.endStruct()) {
                                switch (reader.nextName()) {
                                    case "posts": {
                                        posts = E444ModelMapper.createPosts(reader, locator, null, data.boardName);
                                        break;
                                    }
                                    default: {
                                        reader.skip();
                                        break;
                                    }
                                }
                            }
                            while (!reader.endStruct()) {
                                reader.skip();
                            }
                            break;
                        }
                        case "counter_posters": {
                            uniquePosters = reader.nextInt();
                            break;
                        }
                        case "board": {
                            parseBoardAndApplyReactionIcons(reader, data.boardName, boardConfiguration);
                            break;
                        }
                        default: {
                            reader.skip();
                            break;
                        }
                    }
                }
            }
            configuration.updateFromThreadsPostsJson(data.boardName, boardConfiguration);
            return new ReadPostsResult(new Posts(posts).setUniquePosters(uniquePosters));
        } catch (ParseException e) {
            throw new InvalidResponseException(e);
        } catch (IOException e) {
            throw response.fail(e);
        }
    }

    @Override
    public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {
        E444ChanLocator locator = E444ChanLocator.get(this);
        E444ChanConfiguration configuration = E444ChanConfiguration.get(this);
        HttpResponse response =
                E444IpRequestPerformer.request(data, "index.json").perform();
        try (InputStream input = response.open();
                JsonSerial.Reader reader = JsonSerial.reader(input)) {
            HashMap<String, ArrayList<Board>> boardsMap = new HashMap<>();
            reader.startObject();
            if (!reader.nextName().equals("boards")) {
                throw new InvalidResponseException();
            }
            reader.startArray();
            while (!reader.endStruct()) {
                String category = null;
                String boardName = null;
                String title = null;
                String description = null;
                String defaultName = null;
                Integer bumpLimit = null;
                reader.startObject();
                while (!reader.endStruct()) {
                    switch (reader.nextName()) {
                        case "category": {
                            category = reader.nextString();
                            break;
                        }
                        case "id": {
                            boardName = reader.nextString();
                            break;
                        }
                        case "name": {
                            title = reader.nextString();
                            break;
                        }
                        case "info": {
                            description = reader.nextString();
                            break;
                        }
                        case "default_name": {
                            defaultName = reader.nextString();
                            break;
                        }
                        case "bump_limit": {
                            bumpLimit = reader.nextInt();
                            break;
                        }
                        default: {
                            reader.skip();
                            break;
                        }
                    }
                }
                if (!StringUtils.isEmpty(category) && !StringUtils.isEmpty(boardName) && !StringUtils.isEmpty(title)) {
                    ArrayList<Board> boards = boardsMap.get(category);
                    if (boards == null) {
                        boards = new ArrayList<>();
                        boardsMap.put(category, boards);
                    }
                    description = configuration.transformBoardDescription(description);
                    boards.add(new Board(boardName, title, description));
                    configuration.updateFromBoardsJson(boardName, defaultName, bumpLimit);
                }
            }
            ArrayList<BoardCategory> boardCategories = new ArrayList<>();
            for (String title : PREFERRED_BOARDS_ORDER) {
                for (HashMap.Entry<String, ArrayList<Board>> entry : boardsMap.entrySet()) {
                    if (title.equals(entry.getKey())) {
                        ArrayList<Board> boards = entry.getValue();
                        Collections.sort(boards);
                        boardCategories.add(new BoardCategory(title, boards));
                        break;
                    }
                }
            }
            return new ReadBoardsResult(boardCategories);
        } catch (ParseException e) {
            throw new InvalidResponseException(e);
        } catch (IOException e) {
            throw response.fail(e);
        }
    }

    @Override
    public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
        String description = "Select image where puzzle piece fits the gap";
        return E444CaptchaReader.onReadCaptcha(
                this, data, images -> requireUserImageSingleChoice(-1, images, description, null));
    }

    @Override
    public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException {
        Uri uri = data.uri;
        String host = uri.getHost();
        if (!StringUtils.isEmpty(host)
                && (E444ChanLocator.CHAN_HOST.equals(host) || E444Web3HostResolver.isResolvedHost(host))) {
            String encodedPath = uri.getEncodedPath();
            String[] pathParts;
            if (StringUtils.isEmpty(encodedPath) || "/".equals(encodedPath)) {
                pathParts = new String[0];
            } else {
                pathParts = (encodedPath.startsWith("/") ? encodedPath.substring(1) : encodedPath).split("/");
            }
            E444IpRequestPerformer request = E444IpRequestPerformer.request(data, pathParts);
            for (String queryName : uri.getQueryParameterNames()) {
                for (String queryValue : uri.getQueryParameters(queryName)) {
                    request.param(queryName, queryValue);
                }
            }
            HttpResponse response = request.perform();
            return new ReadContentResult(response);
        }
        return super.onReadContent(data);
    }

    private static final Pattern PATTERN_BAN = Pattern.compile("([^ ]*?): (.*?)(?:\\.|$)");

    private static final SimpleDateFormat DATE_FORMAT_BAN;

    static {
        DATE_FORMAT_BAN = new SimpleDateFormat("d/M/yy HH:mm:ss", Locale.US);
        DATE_FORMAT_BAN.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }

    @Override
    public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
        E444ChanConfiguration configuration = E444ChanConfiguration.get(this);
        String session = E444CaptchaSession.get(configuration);
        E444CaptchaSender.CaptchaPostData captchaPostData = E444CaptchaSender.buildCaptchaPostData(data, session);
        session = captchaPostData.getSession();

        MultipartEntity entity = new MultipartEntity();
        entity.add("task", "post");
        entity.add("board", StringUtils.emptyIfNull(data.boardName));
        entity.add("thread", data.threadNumber != null ? data.threadNumber : "0");
        entity.add("usercode", "");
        captchaPostData.addToEntity(entity);
        entity.add("code", "");
        entity.add("client", "dashchan");
        entity.add("enable_poll", "0");
        entity.add("hat", "");
        entity.add("timer", "0");
        entity.add("email", StringUtils.emptyIfNull(data.email));
        entity.add("trip", "");
        entity.add("subject", StringUtils.emptyIfNull(data.subject));
        entity.add("comment", StringUtils.emptyIfNull(data.comment));
        entity.add("poll_answers[]", "");
        entity.add("makaka_id", "");
        entity.add("makaka_answer", "");
        if (data.attachments != null) {
            for (int i = 0; i < data.attachments.length; i++) {
                data.attachments[i].addToEntity(entity, "file[]");
            }
        }

        E444ChanLocator locator = E444ChanLocator.get(this);
        String requestSession = session;
        HttpResponse response = E444IpRequestPerformer.request(data, "user", "posting")
                .configure(request -> request.setPostMethod(entity)
                        .addCookie(E444CaptchaSession.COOKIE_SESSION, requestSession)
                        .setRedirectHandler(HttpRequest.RedirectHandler.STRICT))
                .perform();
        E444CaptchaSession.updateAndStore(configuration, response, requestSession);
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(response.readString());
        } catch (JSONException e) {
            throw new InvalidResponseException(e);
        }
        String postNumber = CommonUtils.optJsonString(jsonObject, "num");
        if (!StringUtils.isEmpty(postNumber)) {
            return new SendPostResult(data.threadNumber, postNumber);
        }
        String threadNumber = CommonUtils.optJsonString(jsonObject, "thread");
        if (!StringUtils.isEmpty(threadNumber)) {
            return new SendPostResult(threadNumber, null);
        }

        JSONObject errorObject = jsonObject.optJSONObject("error");
        if (errorObject == null) {
            throw new InvalidResponseException();
        }
        int error = Math.abs(errorObject.optInt("code", Integer.MAX_VALUE));
        String reason = CommonUtils.optJsonString(errorObject, "message");
        int errorType = 0;
        Object extra = null;
        switch (error) {
            case 2: {
                errorType = ApiException.SEND_ERROR_NO_BOARD;
                break;
            }
            case 3: {
                errorType = ApiException.SEND_ERROR_NO_THREAD;
                break;
            }
            case 4: {
                errorType = ApiException.SEND_ERROR_NO_ACCESS;
                break;
            }
            case 5: {
                errorType = ApiException.SEND_ERROR_CAPTCHA;
                break;
            }
            case 6: {
                errorType = ApiException.SEND_ERROR_BANNED;
                break;
            }
            case 7: {
                errorType = ApiException.SEND_ERROR_CLOSED;
                break;
            }
            case 8: {
                errorType = ApiException.SEND_ERROR_TOO_FAST;
                break;
            }
            case 9: {
                errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
                break;
            }
            case 11: {
                errorType = ApiException.SEND_ERROR_FILE_NOT_SUPPORTED;
                break;
            }
            case 13: {
                errorType = ApiException.SEND_ERROR_FILES_TOO_MANY;
                break;
            }
            case 19: {
                errorType = ApiException.SEND_ERROR_EMPTY_FILE;
                break;
            }
            case 20: {
                errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
                break;
            }
        }
        if (error == 6) {
            ApiException.BanExtra banExtra = new ApiException.BanExtra();
            Matcher matcher = PATTERN_BAN.matcher(reason);
            while (matcher.find()) {
                String name = StringUtils.emptyIfNull(matcher.group(1));
                String value = StringUtils.emptyIfNull(matcher.group(2));
                if ("Бан".equals(name)) {
                    banExtra.setId(value);
                } else if ("Причина".equals(name)) {
                    banExtra.setMessage(value);
                } else if ("Истекает".equals(name)) {
                    try {
                        long date = Objects.requireNonNull(DATE_FORMAT_BAN.parse(value))
                                .getTime();
                        banExtra.setExpireDate(date);
                    } catch (java.text.ParseException e) {
                        // Ignore exception
                    }
                }
            }
            extra = banExtra;
        }
        if (errorType != 0) {
            throw new ApiException(errorType, extra);
        }
        if (!StringUtils.isEmpty(reason)) {
            throw new ApiException(reason);
        }
        throw new InvalidResponseException();
    }

    @Override
    public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data)
            throws HttpException, ApiException, InvalidResponseException {
        E444ChanLocator locator = E444ChanLocator.get(this);
        E444ChanConfiguration configuration = E444ChanConfiguration.get(this);
        String session = E444CaptchaSession.get(configuration);
        MultipartEntity entity = new MultipartEntity(
                "task",
                "delete",
                "board",
                data.boardName,
                "thread",
                data.threadNumber,
                "postnum",
                data.postNumbers.get(0));
        HttpResponse response = E444IpRequestPerformer.request(data, "api", "posting")
                .configure(request -> request.setPostMethod(entity)
                        .addCookie(E444CaptchaSession.COOKIE_SESSION, session)
                        .setRedirectHandler(HttpRequest.RedirectHandler.STRICT))
                .perform();
        E444CaptchaSession.updateAndStore(configuration, response, session);
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(response.readString());
            int error = Math.abs(jsonObject.optInt("Error", Integer.MAX_VALUE));
            String reason = jsonObject.optString("Reason");
            if (StringUtils.isEmpty(reason)) {
                return null;
            }
            int errorType = 0;
            switch (error) {
                case 2:
                case 21: {
                    errorType = ApiException.DELETE_ERROR_NO_ACCESS;
                }
            }
            if (errorType != 0) {
                throw new ApiException(errorType);
            }
            throw new ApiException(reason);
        } catch (JSONException e) {
            throw new InvalidResponseException(e);
        }
    }

    @Override
    public SendReportPostsResult onSendReportPosts(SendReportPostsData data)
            throws HttpException, ApiException, InvalidResponseException {
        E444ChanLocator locator = E444ChanLocator.get(this);
        StringBuilder postsBuilder = new StringBuilder();
        for (String postNumber : data.postNumbers) {
            postsBuilder.append(postNumber).append(", ");
        }
        MultipartEntity entity = new MultipartEntity(
                "task",
                "report",
                "board",
                data.boardName,
                "thread",
                data.threadNumber,
                "posts",
                postsBuilder.toString(),
                "comment",
                data.comment);
        JSONObject jsonObject;
        try {
            HttpResponse response = E444IpRequestPerformer.request(data, "api", "posting")
                    .configure(request ->
                            request.setPostMethod(entity).setRedirectHandler(HttpRequest.RedirectHandler.STRICT))
                    .perform();
            jsonObject = new JSONObject(response.readString());
            int error = Math.abs(jsonObject.optInt("Error", Integer.MAX_VALUE));
            String reason = jsonObject.optString("Reason");
            if (StringUtils.isEmpty(reason) || "Reported".equals(reason)) {
                return null;
            }
            throw new ApiException(reason);
        } catch (JSONException e) {
            throw new InvalidResponseException(e);
        }
    }
}
