package com.mishiranu.dashchan.chan.e444;

import android.net.Uri;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.SimpleEntity;
import chan.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bouncycastle.jcajce.provider.digest.Keccak;

final class E444Web3HostResolver {
    private static final String WEB3_DOMAIN = "ech.u";
    private static final String RECORD_DNS_A = "dns.A";
    private static final String FUNCTION_SIGNATURE_GET = "get(string,uint256)";
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final byte[] FUNCTION_SELECTOR_GET = Arrays.copyOf(sha3(FUNCTION_SIGNATURE_GET.getBytes()), 4);

    private static final String UNS_PROXY = "0xF6c1b83977DE3dEffC476f5048A0a84d3375d498";

    private static final Uri[] BASE_RPC_URIS = {
        Uri.parse("https://base.rpc.blxrbdn.com"),
        Uri.parse("https://api.zan.top/base-mainnet"),
        //Uri.parse("https://base.api.pocket.network")
    };

    private static final long CACHE_MAX_AGE = 5L * 60L * 1000L;
    private static final long STALE_CACHE_MAX_AGE = 24L * 60L * 60L * 1000L;

    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" + "\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
                    + "\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" + "\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

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
        BigInteger tokenId = new BigInteger(1, calculateNamehash(WEB3_DOMAIN));
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

    private static String resolveDnsARecord(HttpRequest.Preset preset, Uri rpcUri, BigInteger tokenId)
            throws HttpException {
        String callData = encodeGetFunctionCall(RECORD_DNS_A, tokenId);
        String encodedResult = performEthCall(preset, rpcUri, UNS_PROXY, callData);
        return decodeSingleStringResult(encodedResult);
    }

    private static String performEthCall(HttpRequest.Preset preset, Uri rpcUri, String to, String data)
            throws HttpException {
        E444Model.RpcRequest payload = new E444Model.RpcRequest(to, data);
        SimpleEntity entity = new SimpleEntity();
        try {
            entity.setData(E444JsonUtils.toJson(payload));
        } catch (RuntimeException e) {
            throw new HttpException(0, "Unable to build RPC payload");
        }
        entity.setContentType("application/json");
        String responseText =
                new HttpRequest(rpcUri, preset).setPostMethod(entity).perform().readString();
        try {
            E444Model.RpcResponse responseJson = E444JsonUtils.fromJson(responseText, E444Model.RpcResponse.class);
            E444Model.RpcError error = responseJson != null ? responseJson.error : null;
            if (error != null) {
                String message = StringUtils.nullIfEmpty(error.message);
                throw new HttpException(0, message != null ? message : "RPC eth_call failed");
            }
            String result = StringUtils.nullIfEmpty(responseJson != null ? responseJson.result : null);
            if (result == null) {
                throw new HttpException(0, "RPC eth_call returned empty result");
            }
            return result;
        } catch (IOException e) {
            throw new HttpException(0, "Invalid RPC response JSON");
        }
    }

    private static List<String> parseIpv4Record(String recordValue) throws HttpException {
        try {
            List<String> jsonArray = E444JsonUtils.fromJson(recordValue, new TypeReference<List<String>>() {});
            if (jsonArray == null) {
                throw new HttpException(0, "Record " + RECORD_DNS_A + " is not a valid JSON array");
            }
            LinkedHashSet<String> hosts = new LinkedHashSet<>();
            for (String value : jsonArray) {
                String host = StringUtils.nullIfEmpty(value);
                if (host != null) {
                    host = host.trim();
                    if (IPV4_PATTERN.matcher(host).matches()) {
                        hosts.add(host);
                    }
                }
            }
            return new ArrayList<>(hosts);
        } catch (IOException e) {
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

    private static String encodeGetFunctionCall(String key, BigInteger tokenId) throws HttpException {
        if (StringUtils.isEmpty(key)) {
            throw new HttpException(0, "Invalid UNS record key");
        }
        if (tokenId == null || tokenId.signum() < 0) {
            throw new HttpException(0, "Invalid UNS token id");
        }

        byte[] keyBytes = key.getBytes();
        int paddedKeyLength = ((keyBytes.length + 31) / 32) * 32;
        int payloadLength = 64 + 32 + paddedKeyLength;
        byte[] payload = new byte[4 + payloadLength];

        System.arraycopy(FUNCTION_SELECTOR_GET, 0, payload, 0, 4);
        writeUInt256(payload, 4, BigInteger.valueOf(64L));
        writeUInt256(payload, 36, tokenId);
        writeUInt256(payload, 68, BigInteger.valueOf(keyBytes.length));
        System.arraycopy(keyBytes, 0, payload, 100, keyBytes.length);

        return "0x" + bytesToHex(payload);
    }

    private static String decodeSingleStringResult(String encoded) throws HttpException {
        byte[] data = decodeHex(encoded);
        if (data.length == 0) {
            return null;
        }
        if (data.length < 64) {
            throw new HttpException(0, "Invalid ABI response data");
        }
        int offset = readUInt256AsInt(data, 0);
        if (offset < 0 || offset + 32 > data.length) {
            throw new HttpException(0, "Invalid ABI response data");
        }
        int stringLength = readUInt256AsInt(data, offset);
        int stringOffset = offset + 32;
        if (stringLength < 0 || stringOffset + stringLength > data.length) {
            throw new HttpException(0, "Invalid ABI response data");
        }
        return StringUtils.nullIfEmpty(new String(data, stringOffset, stringLength));
    }

    private static void writeUInt256(byte[] output, int offset, BigInteger value) throws HttpException {
        byte[] valueBytes = value.toByteArray();
        int sourceOffset = 0;
        if (valueBytes.length > 32) {
            if (valueBytes.length == 33 && valueBytes[0] == 0) {
                sourceOffset = 1;
            } else {
                throw new HttpException(0, "Invalid uint256 value");
            }
        }
        int length = valueBytes.length - sourceOffset;
        if (length > 32) {
            throw new HttpException(0, "Invalid uint256 value");
        }
        System.arraycopy(valueBytes, sourceOffset, output, offset + 32 - length, length);
    }

    private static int readUInt256AsInt(byte[] input, int offset) throws HttpException {
        if (offset < 0 || offset + 32 > input.length) {
            throw new HttpException(0, "Invalid ABI response data");
        }
        for (int i = offset; i < offset + 28; i++) {
            if (input[i] != 0) {
                throw new HttpException(0, "Invalid ABI response data");
            }
        }
        int value = 0;
        for (int i = offset + 28; i < offset + 32; i++) {
            value = (value << 8) | (input[i] & 0xff);
        }
        return value;
    }

    private static byte[] decodeHex(String value) throws HttpException {
        if (StringUtils.isEmpty(value)) {
            throw new HttpException(0, "RPC eth_call returned empty result");
        }
        String hex = value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
        if (hex.length() % 2 != 0) {
            throw new HttpException(0, "Invalid ABI response data");
        }
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = hexDigit(hex.charAt(i * 2));
            int low = hexDigit(hex.charAt(i * 2 + 1));
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            chars[i * 2] = HEX_DIGITS[value >>> 4];
            chars[i * 2 + 1] = HEX_DIGITS[value & 0x0f];
        }
        return new String(chars);
    }

    private static int hexDigit(char c) throws HttpException {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        throw new HttpException(0, "Invalid HEX");
    }

    private static byte[] sha3(byte[] input) {
        return new Keccak.Digest256().digest(input);
    }
}
