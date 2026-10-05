package com.linewell.dataelement.platform.magic.module;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/**
 * @Description: 缓存模块
 * @Author: Hjiahao
 * @Date: 2024/10/23 14:51:11
 * @Copyright: Fujian Linewell Software Co., Ltd. All rights reserved.
 */
@Component
@MagicModule("CacheModule")
@Slf4j
public class CacheModule {

	private final static TimedCache<String, String> cache = CacheUtil.newTimedCache(5000 * 1000);

	@Comment("getCache")
	public String getCache(@Comment("key")String key) {
		return cache.get(key);
	}

	@Comment("putCache")
	public void putCache(@Comment("key") String key, @Comment("value") String value) {
		cache.put(key, value);
	}

}
