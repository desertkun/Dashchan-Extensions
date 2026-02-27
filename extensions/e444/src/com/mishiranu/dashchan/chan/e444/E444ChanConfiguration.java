package com.mishiranu.dashchan.chan.e444;

import android.util.Log;
import chan.content.ChanConfiguration;
import chan.util.StringUtils;
import com.mishiranu.dashchan.chan.e444.enhance.HostBridge;
import java.util.Locale;

public class E444ChanConfiguration extends ChanConfiguration {
    public static final String CAPTCHA_TYPE_SLIDER = "ech";

    private static final String KEY_FILES_ENABLED = "files_enabled";
    private static final String KEY_NAMES_ENABLED = "names_enabled";
    private static final String KEY_POSTING_ENABLED = "enable_posting";
    private static final String KEY_DELETING_ENABLED = "deleting_enabled";
    private static final String KEY_MAX_COMMENT_LENGTH = "max_comment";

    public E444ChanConfiguration() {
        HostBridge.ensureActivityHookInstalled();
        request(OPTION_ALLOW_CAPTCHA_PASS);
        setDefaultName("Anonymous");
        setBumpLimit(500);
        addCaptchaType(CAPTCHA_TYPE_SLIDER);
        addCaptchaType(CAPTCHA_TYPE_RECAPTCHA_2);
        addCaptchaType(CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE);
    }

    @Override
    public Board obtainBoardConfiguration(String boardName) {
        ensureHostInjection();
        Board board = new Board();
        board.allowCatalog = true;
        board.allowPosting = true;
        board.allowDeleting = get(boardName, KEY_DELETING_ENABLED, false);
        board.allowReporting = true;
        return board;
    }

    @Override
    public Captcha obtainCustomCaptchaConfiguration(String captchaType) {
        ensureHostInjection();
        if (CAPTCHA_TYPE_SLIDER.equals(captchaType)) {
            Captcha captcha = new Captcha();
            captcha.title = "slider";
            captcha.input = Captcha.Input.NUMERIC;
            captcha.validity = Captcha.Validity.IN_BOARD_SEPARATELY;
            return captcha;
        }
        return null;
    }

    @Override
    public Posting obtainPostingConfiguration(String boardName, boolean newThread) {
        ensureHostInjection();
        Posting posting = new Posting();
        posting.allowName = get(boardName, KEY_NAMES_ENABLED, true);
        posting.allowTripcode = true;
        posting.allowEmail = true;
        posting.allowSubject = true;
        posting.optionSage = true;
        posting.optionOriginalPoster = true;
        posting.maxCommentLength = get(boardName, KEY_MAX_COMMENT_LENGTH, 15000);
        posting.maxCommentLengthEncoding = "UTF-8";
        posting.attachmentCount = get(boardName, KEY_FILES_ENABLED, true) ? 4 : 0;
        posting.attachmentMimeTypes.add("image/jpeg");
        posting.attachmentMimeTypes.add("image/gif");
        posting.attachmentMimeTypes.add("image/png");
        posting.attachmentMimeTypes.add("image/webp");
        posting.attachmentMimeTypes.add("video/webm");
        posting.attachmentMimeTypes.add("video/mp4");
        posting.attachmentMimeTypes.add("audio/mp3");
        posting.attachmentMimeTypes.add("application/ogg");
        posting.attachmentMimeTypes.add("application/zip");
        posting.attachmentMimeTypes.add("application/pdf");
        return posting;
    }

    @Override
    public Deleting obtainDeletingConfiguration(String boardName) {
        ensureHostInjection();
        return new Deleting();
    }

    @Override
    public Reporting obtainReportingConfiguration(String boardName) {
        ensureHostInjection();
        Reporting reporting = new Reporting();
        reporting.comment = true;
        reporting.multiplePosts = true;
        return reporting;
    }

    private void ensureHostInjection() {
        try {
            HostBridge.ensureActivityHookInstalled(getContext());
        } catch (Throwable t) {
            Log.w("E444ChanConfiguration", "Unable to install host activity hook", t);
        }
    }

    public void updateFromBoardsJson(String boardName, String defaultName, Integer bumpLimit) {
        if (!StringUtils.isEmpty(defaultName)) {
            storeDefaultName(boardName, defaultName);
        }
        if (bumpLimit != null && bumpLimit > 0) {
            storeBumpLimit(boardName, bumpLimit);
        }
    }

    public void updateFromThreadsPostsJson(String boardName, E444ModelMapper.BoardConfiguration configuration) {
        String description = transformBoardDescription(configuration.description);
        if (!StringUtils.isEmpty(configuration.title)) {
            storeBoardTitle(boardName, configuration.title);
        }
        if (!StringUtils.isEmpty(description)) {
            storeBoardDescription(boardName, description);
        }
        if (!StringUtils.isEmpty(configuration.defaultName)) {
            storeDefaultName(boardName, configuration.defaultName);
        }
        if (configuration.bumpLimit > 0) {
            storeBumpLimit(boardName, configuration.bumpLimit);
        }
        if (configuration.maxCommentLength > 0) {
            set(boardName, KEY_MAX_COMMENT_LENGTH, configuration.maxCommentLength);
        }
        editBoards(boardName, KEY_FILES_ENABLED, configuration.filesEnabled);
        editBoards(boardName, KEY_NAMES_ENABLED, configuration.namesEnabled);
        editBoards(boardName, KEY_POSTING_ENABLED, configuration.postingEnabled);
        editBoards(boardName, KEY_DELETING_ENABLED, configuration.deletingEnabled);
    }

    private void editBoards(String boardName, String key, Boolean value) {
        if (value != null) {
            set(boardName, key, value);
        }
    }

    public String transformBoardDescription(String description) {
        description = StringUtils.nullIfEmpty(StringUtils.clearHtml(description).trim());
        if (description != null) {
            StringBuilder builder = new StringBuilder();
            builder.append(description.substring(0, 1).toUpperCase(Locale.getDefault()));
            builder.append(description.substring(1));
            if (!description.endsWith(".") && !description.endsWith("!")) {
                builder.append(".");
            }
            description = builder.toString();
        }
        return description;
    }
}
