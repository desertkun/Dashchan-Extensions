package com.mishiranu.dashchan.chan.e444;

import android.net.Uri;

import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import java.util.List;

public final class E444IpRequestPerformer {
	@FunctionalInterface
	public interface UriFactory {
		Uri create(E444ChanLocator locator, String host);
	}

	@FunctionalInterface
	public interface RequestConfigurator {
		HttpRequest configure(HttpRequest request);
	}

	private E444IpRequestPerformer() {}

	public static HttpResponse perform(E444ChanLocator locator, HttpRequest.Preset preset, UriFactory uriFactory,
			RequestConfigurator requestConfigurator) throws HttpException {
		List<String> hosts = E444Web3HostResolver.resolveHosts(preset);
		HttpException lastSocketException = null;
		for (String host : hosts) {
			try {
				HttpRequest request = new HttpRequest(uriFactory.create(locator, host), preset);
				if (requestConfigurator != null) {
					request = requestConfigurator.configure(request);
				}
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

	public static Uri rewriteUriHost(Uri uri, String host) {
		int port = uri.getPort();
		String authority = port >= 0 ? host + ":" + port : host;
		return uri.buildUpon().scheme("https").authority(authority).build();
	}
}
