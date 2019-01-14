package com.ecidi.cim.tokenproxy.util;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Map;

@Repository
public class TokenCacheUtil {
    private static final String KEY = "SuperMapTokenAuthInfo";

    @Resource(name = "redisHashTemplate")
    private static HashOperations<String, String, Boolean> hashOps;

    public static Boolean hasTokenInfo(String tokenUri) {
        return hashOps.hasKey(KEY, tokenUri);
    }

    public static void addTokenInfo(String tokenUri, Boolean authInfo) {
        hashOps.putIfAbsent(KEY, tokenUri, authInfo);
    }

    public static void updateTokenInfo(String tokenUri, Boolean authInfo) {
        hashOps.put(KEY, tokenUri, authInfo);
    }

    public static Boolean getTokenInfo(String tokenUri) {
        return hashOps.get(KEY, tokenUri);
    }

    public static Map<String, Boolean> getAllTokenInfos() {
        return hashOps.entries(KEY);
    }

    public static long deleteTokenInfos(String... tokenUris) {
        return hashOps.delete(KEY, (Object) tokenUris);
    }
}