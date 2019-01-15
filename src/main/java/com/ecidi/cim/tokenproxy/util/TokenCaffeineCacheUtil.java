package com.ecidi.cim.tokenproxy.util;

import com.ecidi.cim.tokenproxy.handler.ProxyServerHandler;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TokenCaffeineCacheUtil {

	//创建缓存
	private static final LoadingCache<String, Boolean> cache = Caffeine.newBuilder()
			.initialCapacity(1024)
			//不限制缓存大小
//			.maximumSize(10240)
			//超过x秒未读写操作,自动删除
			.expireAfterWrite(2, TimeUnit.HOURS)
			//传入缓存加载策略,key不存在时调用该方法返回一个value回去
			//此处直接返回空
			.build(key -> null);

//	public Boolean hasTokenInfo(String tokenUri) {
//		return cache.getIfPresent(tokenUri);
//	}

//	public void updateTokenInfo(String tokenUri, Boolean authInfo) {
//		cache.put();
//	}

	/**
	 * 获取数据
	 */
	public static Boolean getTokenInfo(String tokenUri) {
		return cache.get(tokenUri);
	}

	/**
	 * 存入数据
	 */
	public static void setTokenInfo(String tokenUri, Boolean authInfo) {
		cache.put(tokenUri, authInfo);
	}

	/**
	 * 删除数据
	 */
	public static void deleteTokenInfo(String authInfo) {
		cache.invalidate(authInfo);
	}

}
