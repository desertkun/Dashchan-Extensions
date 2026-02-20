package com.mishiranu.dashchan.chan.e444;

import android.net.Uri;

import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.SimpleEntity;
import chan.util.StringUtils;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import java.math.BigInteger;

final class E444Web3HostResolver {
	private static final String WEB3_DOMAIN = "ech.u";
	private static final String RECORD_DNS_A = "dns.A";

	private static final String UNS_PROXY = "0xF6c1b83977DE3dEffC476f5048A0a84d3375d498";

	private static final Uri[] BASE_RPC_URIS = {
			Uri.parse("https://mainnet.base.org"),
			Uri.parse("https://base-rpc.publicnode.com"),
			Uri.parse("https://base.llamarpc.com")
	};

	private static final long CACHE_MAX_AGE = 5L * 60L * 1000L;
	private static final long STALE_CACHE_MAX_AGE = 24L * 60L * 60L * 1000L;

	private static final Pattern IPV4_PATTERN = Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
			+ "\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
			+ "\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
			+ "\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

	private static volatile Cache cache;
	private static final Object LOCK = new Object();

	private static final class Cache {
		public final List<String> hosts;
		public final long timestamp;

		private Cache(List<String> hosts, long timestamp) {
			this.hosts = Collections.unmodifiableList(hosts);
			this.timestamp = timestamp;
		}
	}

	private E444Web3HostResolver() {}

	public static List<String> resolveHosts(HttpRequest.Preset preset) throws HttpException {
		long now = System.currentTimeMillis();
		Cache localCache = cache;
		if (isCacheValid(localCache, now, CACHE_MAX_AGE)) {
			return localCache.hosts;
		}
		synchronized (LOCK) {
			localCache = cache;
			if (isCacheValid(localCache, now, CACHE_MAX_AGE)) {
				return localCache.hosts;
			}
			HttpException lastException = null;
			for (Uri rpcUri : BASE_RPC_URIS) {
				try {
					List<String> hosts = resolveHostsViaRpc(preset, rpcUri);
					if (!hosts.isEmpty()) {
						Cache newCache = new Cache(new ArrayList<>(hosts), now);
						cache = newCache;
						return newCache.hosts;
					}
				} catch (HttpException e) {
					lastException = e;
				}
			}
			localCache = cache;
			if (isCacheValid(localCache, now, STALE_CACHE_MAX_AGE)) {
				return localCache.hosts;
			}
			if (lastException != null) {
				throw lastException;
			}
			throw new HttpException(0, "Failed to resolve web3 hosts");
		}
	}

	public static boolean isResolvedHost(String host) {
		Cache localCache = cache;
		return localCache != null && localCache.hosts.contains(host);
	}

	private static boolean isCacheValid(Cache cache, long now, long maxAge) {
		return cache != null && !cache.hosts.isEmpty() && now - cache.timestamp <= maxAge;
	}

	private static List<String> resolveHostsViaRpc(HttpRequest.Preset preset, Uri rpcUri) throws HttpException {
		Uint256 tokenId = new Uint256(new BigInteger(1, calculateNamehash(WEB3_DOMAIN)));
		String recordValue = resolveDnsARecord(preset, rpcUri, tokenId);
		if (StringUtils.isEmpty(recordValue)) {
			throw new HttpException(0, "Record " + RECORD_DNS_A + " is empty for " + WEB3_DOMAIN);
		}
		List<String> hosts = parseIpv4Record(recordValue);
		if (hosts.isEmpty()) {
			throw new HttpException(0, "Record " + RECORD_DNS_A + " has no valid IPv4 hosts for " + WEB3_DOMAIN);
		}
		return hosts;
	}

	private static String resolveDnsARecord(HttpRequest.Preset preset, Uri rpcUri, Uint256 tokenId)
			throws HttpException {
		Function getFunction = new Function("get", Arrays.asList(new Utf8String(RECORD_DNS_A), tokenId),
				Collections.singletonList(new TypeReference<Utf8String>() {}));
		String encoded = performEthCall(preset, rpcUri, UNS_PROXY, FunctionEncoder.encode(getFunction));
		List<Type> decoded = decodeFunctionResult(encoded, getFunction, false);
		if (decoded == null || decoded.isEmpty() || !(decoded.get(0) instanceof Utf8String)) {
			return null;
		}
		return StringUtils.nullIfEmpty(((Utf8String) decoded.get(0)).getValue());
	}

	private static List<Type> decodeFunctionResult(String encoded, Function function, boolean allowEmpty)
			throws HttpException {
		try {
			List<Type> decoded = FunctionReturnDecoder.decode(encoded, function.getOutputParameters());
			if (decoded.isEmpty() && !allowEmpty) {
				return null;
			}
			return decoded;
		} catch (RuntimeException e) {
			if (allowEmpty) {
				return null;
			}
			throw new HttpException(0, "Invalid ABI response data");
		}
	}

	private static String performEthCall(HttpRequest.Preset preset, Uri rpcUri, String to, String data)
			throws HttpException {
		JSONObject payload = new JSONObject();
		JSONObject transaction = new JSONObject();
		JSONArray params = new JSONArray();
		try {
			transaction.put("to", to);
			transaction.put("data", data);
			params.put(transaction);
			params.put("latest");
			payload.put("jsonrpc", "2.0");
			payload.put("id", 1);
			payload.put("method", "eth_call");
			payload.put("params", params);
		} catch (JSONException e) {
			throw new HttpException(0, "Unable to build RPC payload");
		}
		SimpleEntity entity = new SimpleEntity();
		entity.setData(payload.toString());
		entity.setContentType("application/json");
		String responseText = new HttpRequest(rpcUri, preset).setPostMethod(entity).perform().readString();
		try {
			JSONObject responseJson = new JSONObject(responseText);
			JSONObject error = responseJson.optJSONObject("error");
			if (error != null) {
				String message = StringUtils.nullIfEmpty(error.optString("message"));
				throw new HttpException(0, message != null ? message : "RPC eth_call failed");
			}
			String result = StringUtils.nullIfEmpty(responseJson.optString("result"));
			if (result == null) {
				throw new HttpException(0, "RPC eth_call returned empty result");
			}
			return result;
		} catch (JSONException e) {
			throw new HttpException(0, "Invalid RPC response JSON");
		}
	}

	private static List<String> parseIpv4Record(String recordValue) throws HttpException {
		try {
			JSONArray jsonArray = new JSONArray(recordValue);
			LinkedHashSet<String> hosts = new LinkedHashSet<>();
			for (int i = 0; i < jsonArray.length(); i++) {
				String host = StringUtils.nullIfEmpty(jsonArray.optString(i, null));
				if (host != null) {
					host = host.trim();
					if (IPV4_PATTERN.matcher(host).matches()) {
						hosts.add(host);
					}
				}
			}
			return new ArrayList<>(hosts);
		} catch (JSONException e) {
			throw new HttpException(0, "Record " + RECORD_DNS_A + " is not a valid JSON array");
		}
	}

	private static byte[] calculateNamehash(String domain) {
		if (StringUtils.isEmpty(domain)) {
			return new byte[32];
		}
		int index = domain.indexOf('.');
		String label = index >= 0 ? domain.substring(0, index) : domain;
		String remainder = index >= 0 ? domain.substring(index + 1) : "";
		label = label.toLowerCase(Locale.US);
		byte[] remainderHash = calculateNamehash(remainder);
		byte[] labelHash = sha3(label.getBytes());
		byte[] input = new byte[64];
		System.arraycopy(remainderHash, 0, input, 0, 32);
		System.arraycopy(labelHash, 0, input, 32, 32);
		return sha3(input);
	}

	private static byte[] sha3(byte[] input) {
		return new Keccak.Digest256().digest(input);
	}
}
