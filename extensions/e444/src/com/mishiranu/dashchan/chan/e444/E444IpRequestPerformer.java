package com.mishiranu.dashchan.chan.e444;

import android.net.Uri;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class E444IpRequestPerformer {
    @FunctionalInterface
    public interface RequestConfigurator {
        HttpRequest configure(HttpRequest request);
    }

    private final HttpRequest.Preset preset;
    private final String[] pathParts;
    private final LinkedHashMap<String, String> queryParameters = new LinkedHashMap<>();
    private RequestConfigurator requestConfigurator;

    private E444IpRequestPerformer(HttpRequest.Preset preset, String... pathParts) {
        this.preset = preset;
        this.pathParts = pathParts;
    }

    public static E444IpRequestPerformer request(HttpRequest.Preset preset, String... pathParts) {
        return new E444IpRequestPerformer(preset, pathParts);
    }

    public E444IpRequestPerformer param(String key, String value) {
        queryParameters.put(key, value);
        return this;
    }

    public E444IpRequestPerformer configure(RequestConfigurator requestConfigurator) {
        this.requestConfigurator = requestConfigurator;
        return this;
    }

    public HttpResponse perform() throws HttpException {
        List<String> hosts = E444Web3HostResolver.resolveHosts(preset);
        HttpException lastSocketException = null;
        for (String host : hosts) {
            try {
                Uri.Builder uriBuilder = new Uri.Builder().scheme("https").authority(host);
                for (String pathPart : pathParts) {
                    uriBuilder.appendEncodedPath(pathPart);
                }
                for (Map.Entry<String, String> queryParameter : queryParameters.entrySet()) {
                    uriBuilder.appendQueryParameter(queryParameter.getKey(), queryParameter.getValue());
                }
                HttpRequest request = new HttpRequest(uriBuilder.build(), preset);
                if (requestConfigurator != null) {
                    request = requestConfigurator.configure(request);
                }
                request.addCookie("usercode_auth", "foobar");
                return request.perform();
            } catch (HttpException e) {
                if (e.isSocketException()) {
                    lastSocketException = e;
                } else {
                    throw e;
                }
            }
        }
        if (lastSocketException != null) {
            throw lastSocketException;
        }
        throw new HttpException(0, "No resolved IP host is reachable");
    }
}
