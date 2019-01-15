package com.ecidi.cim.tokenproxy.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @class_name:  TokenCacheUtil
 * @package:     com.ecidi.cim.tokenproxy.util
 * @author:      Canxer Huang
 * @department:  Ecidi
 * @date:        2019-1-15 11:42
 * @description: Redis Cache for token authorization info
 *
 */

@Component
public class TokenRedisCacheUtil {
    private static final String KEY = "SuperMapTokenAuthInfo";

    @Autowired
    @Qualifier("redisHashTemplate")
    private HashOperations<String, String, Boolean> hashOperations;

    public Boolean hasTokenInfo(String tokenUri) {
        return hashOperations.hasKey(KEY, tokenUri);
    }

    public void addTokenInfo(String tokenUri, Boolean authInfo) {
        hashOperations.putIfAbsent(KEY, tokenUri, authInfo);
    }

    public void updateTokenInfo(String tokenUri, Boolean authInfo) {
        hashOperations.put(KEY, tokenUri, authInfo);
    }

    public Boolean getTokenInfo(String tokenUri) {
        return hashOperations.get(KEY, tokenUri);
    }

    public Map<String, Boolean> getAllTokenInfos() {
        return hashOperations.entries(KEY);
    }

    public long deleteTokenInfos(String... tokenUris) {
        return hashOperations.delete(KEY, (Object) tokenUris);
    }
}