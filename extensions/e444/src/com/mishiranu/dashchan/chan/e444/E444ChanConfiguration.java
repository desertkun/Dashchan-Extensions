package com.mishiranu.dashchan.chan.e444;

import chan.content.ChanConfiguration;
import chan.util.StringUtils;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceShim;
import com.mishiranu.dashchan.chan.e444.enhance.widgets.WidgetReaction;
import java.io.IOException;

public class E444ChanConfiguration extends ChanConfiguration {
    public static final String CAPTCHA_TYPE_SLIDER = "ech";
    private String lastBoardId;

    public E444ChanConfiguration() {
        EnhanceShim.ensureActivityHookInstalled(getContext());
        request(OPTION_ALLOW_CAPTCHA_PASS);
        addCaptchaType(CAPTCHA_TYPE_SLIDER);
        //        addCaptchaType(CAPTCHA_TYPE_RECAPTCHA_2);
        //        addCaptchaType(CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE);
    }

    @Override
    public Board obtainBoardConfiguration(String boardName) {
        EnhanceShim.ensureActivityHookInstalled(getContext());
        E444Model.Board cachedBoard = getCachedBoard(boardName);
        Board board = new Board();
        board.allowPosting = cachedBoard.enablePosting != 0;
        // board.allowVotes = cachedBoard.enableLikes != 0;
        board.allowCatalog = true;
        board.allowDeleting = true;
        board.allowReporting = true;
        return board;
    }

    @Override
    public Captcha obtainCustomCaptchaConfiguration(String captchaType) {
        EnhanceShim.ensureActivityHookInstalled(getContext());
        if (captchaType.equals(CAPTCHA_TYPE_SLIDER)) {
            Captcha captcha = new Captcha();
            captcha.title = "Slider";
            captcha.input = Captcha.Input.NUMERIC;
            captcha.validity = Captcha.Validity.IN_BOARD_SEPARATELY;
            return captcha;
        }
        return null;
    }

    @Override
    public Posting obtainPostingConfiguration(String boardName, boolean newThread) {
        EnhanceShim.ensureActivityHookInstalled(getContext());
        E444Model.Board cachedBoard = getCachedBoard(boardName);
        Posting posting = new Posting();
        posting.allowName = cachedBoard.enableTrips != 0;
        posting.allowTripcode = cachedBoard.enableTrips != 0;
        posting.allowEmail = true;
        posting.allowSubject = cachedBoard.enableSubject != 0;
        posting.optionSage = cachedBoard.enableSage != 0;
        posting.optionOriginalPoster = true;
        posting.maxCommentLength = cachedBoard.maxComment;
        posting.maxCommentLengthEncoding = "UTF-8";
        posting.attachmentCount = cachedBoard.fileTypes != null && !cachedBoard.fileTypes.isEmpty() ? 4 : 0;
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
        EnhanceShim.ensureActivityHookInstalled(getContext());
        return new Deleting();
    }

    @Override
    public Reporting obtainReportingConfiguration(String boardName) {
        EnhanceShim.ensureActivityHookInstalled(getContext());
        Reporting reporting = new Reporting();
        reporting.comment = true;
        reporting.multiplePosts = true;
        return reporting;
    }

    public void updateFromBoards(E444Model.Board board) {
        WidgetReaction.setBoardReactionIcons(board.id, board.enableReactions != 0 ? board.reactions : null);
        set(board.id, "board_json_cache", E444JsonUtils.toJson(board));
        storeBoardTitle(board.id, board.name);
        storeBoardDescription(board.id, StringUtils.clearHtml(board.info).trim());
        storeDefaultName(board.id, board.defaultName);
        storeBumpLimit(board.id, board.bumpLimit);
        storePagesCount(board.id, board.maxPages);
    }

    private E444Model.Board getCachedBoard(String boardName) {
        if ("e444".equals(boardName)) {
            boardName = lastBoardId;
        }
        try {
            String serialized = get(boardName, "board_json_cache", null);
            E444Model.Board board = E444JsonUtils.fromJson(serialized, E444Model.Board.class);
            lastBoardId = board.id;
            return board;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
