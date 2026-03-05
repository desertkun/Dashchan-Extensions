package com.mishiranu.dashchan.chan.e444;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Arrays;
import java.util.List;

public final class E444Model {
    private E444Model() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Board {
        public String id;
        public String category;
        public String name;
        public String info;
        public String defaultName;
        public List<String> reactions;
        public List<String> fileTypes;

        public int enableDices;
        public int enableFlags;
        public int enableIcons;
        public int enableLikes;
        public int enableReactions;
        public int enableNames;
        public int enableOekaki;
        public int enablePosting;
        public int enableSage;
        public int enableShield;
        public int enableSubject;
        public int enableThreadTags;
        public int enableTrips;
        public int enableOpMod;

        public int bumpLimit;
        public int maxComment;
        public int maxFilesSize;
        public int maxPages;
        public int speed;
        public int threads;
        public int threadsPerPage;
        public int uniquePosters;
        public int lastNum;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Thread {
        public int threadNum;
        public int postsCount;
        public int filesCount;
        public List<Post> posts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Post {
        public int num;
        public String board;
        public int parent;
        public int op;
        public int sticky;
        public int closed;
        public int endless;
        public long timestamp;
        public String subject;
        public String comment;
        public String name;
        public String trip;
        public String email;
        public List<File> files;
        public int postsCount;
        public int filesCount;

        public List<Post> posts;
        public List<MenuSection> menu;
        public List<Reaction> reactions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class File {
        public String path;
        public String thumbnail;
        public String fullname;
        public int size;
        public int width;
        public int height;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BoardsResponse {
        public List<Board> boards;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class PostsResponse {
        public int currentThread;
        public int filesCount;
        public int postersCount;
        public int postsCount;
        public boolean isBoard;
        public boolean isIndex;
        public int isClosed;
        public int moderView;
        public Board board;
        public List<Thread> threads;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ApiResponse {
        public int result;
        public ApiError error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ApiError {
        public int code;
        public String message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class PostingResponse {
        public int result;
        public Integer num;
        public Integer thread;
        public ApiError error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SlideCaptchaResponse {
        public int code;
        public String message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SlideCaptchaIdResponse {
        public int code;
        public String captchaKey;
        public String imageBase64;
        public String tileBase64;
        public int tileWidth;
        public int tileHeight;
        public int tileX;
        public int tileY;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class MobilePostResponse {
        public int result;
        public Post post;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class MenuLink {
        public String label;
        public String url;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static final class MenuSection {
        public String sectionName;
        public List<MenuLink> links;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Reaction {
        public String icon;
        public int count;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class RpcTransaction {
        public String to;
        public String data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class RpcRequest {
        public String jsonrpc = "2.0";
        public int id = 1;
        public String method = "eth_call";
        public List<Object> params;

        public RpcRequest() {}

        public RpcRequest(String to, String data) {
            RpcTransaction transaction = new RpcTransaction();
            transaction.to = to;
            transaction.data = data;
            this.params = Arrays.asList(transaction, "latest");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class RpcResponse {
        public String result;
        public RpcError error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class RpcError {
        public String message;
    }
}
