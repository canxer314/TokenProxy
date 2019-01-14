package com.ecidi.cim.tokenproxy.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TokenCacheUtil {
    private static final String KEY = "SuperMapTokenAuthInfo";

    @Autowired
    private static HashOperations<String, String, Boolean> hashOperations;

    public static Boolean hasTokenInfo(String tokenUri) {
        return hashOperations.hasKey(KEY, tokenUri);
    }

    public static void addTokenInfo(String tokenUri, Boolean authInfo) {
        hashOperations.putIfAbsent(KEY, tokenUri, authInfo);
    }

    public static void updateTokenInfo(String tokenUri, Boolean authInfo) {
        hashOperations.put(KEY, tokenUri, authInfo);
    }

    public static Boolean getTokenInfo(String tokenUri) {
        return hashOperations.get(KEY, tokenUri);
    }

    public static Map<String, Boolean> getAllTokenInfos() {
        return hashOperations.entries(KEY);
    }

    public static long deleteTokenInfos(String... tokenUris) {
        return hashOperations.delete(KEY, (Object) tokenUris);
    }
}