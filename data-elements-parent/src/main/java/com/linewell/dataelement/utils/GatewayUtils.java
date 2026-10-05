package com.linewell.dataelement.utils;

import cn.hutool.crypto.SmUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.linewell.dataelement.dataassets.base.entity.GatewayUserAppRela;
import com.linewell.dataelement.dataassets.base.service.IGatewayUserAppRelaService;
import com.linewell.dataelement.feature.identity.application.IdentityUserLookupService;
import com.linewell.dataelement.feature.identity.domain.IdentityUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.linewell.dataelement.platform.persistence.id.NumericId;

/**
 * @author: sangbo
 * @create: 2026-05-22
 * @description: 网关工具类
 **/
@Slf4j
@Component
public class GatewayUtils {

    /** Redis 中缓存 access_token 的键名 */
    private static final String TOKEN_REDIS_KEY = "gateway:openapi:access_token";

    /** access_token 缓存过期时间（秒），固定 1 小时 */
    private static final long TOKEN_CACHE_EXPIRE_SECONDS = 3600L;

    @Value("${gateway.openapi.appkey:}")
    private String appkey;
    @Value("${gateway.openapi.secretkey:}")
    private String secretkey;
    @Value("${gateway.openapi.url:}")
    private String openapiUrl;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private IdentityUserLookupService identityUserLookupService;

    @Autowired
    private IGatewayUserAppRelaService gatewayUserAppRelaService;

    public String v2CreateAuthorization(HashMap<String, String> map) {
        map.put("action", "V2CreateAuthorization");
        map.put("token", getToken());
        return fillQueryParams(map,openapiUrl);
    }

    /**
     * 获取 access_token，完整流程：
     * 1. 按签名规则请求网关创建令牌
     * 2. 调用 {@link #handleTokenResponse(String)} 处理返回的 JSON 响应
     *
     * @return access_token
     */
    public String getToken() {
        requireGatewayConfiguration();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = SmUtil.sm3(secretkey + timestamp);
        HashMap<String, String> map = new HashMap<>();
        map.put("action", "CreateToken");
        map.put("sign", sign);
        map.put("timestamp", timestamp);
        map.put("app_key", appkey);
        String url = fillQueryParams(map, openapiUrl);
        String response = HttpUtil.post(url, "");
        return handleTokenResponse(response);
    }

    /**
     * 网关对接为可选集成：未配置时不应阻断平台启动，实际调用时再明确提示缺失项。
     */
    private void requireGatewayConfiguration() {
        if (StringUtils.isBlank(appkey) || StringUtils.isBlank(secretkey) || StringUtils.isBlank(openapiUrl)) {
            throw new IllegalStateException(
                    "未配置网关 OpenAPI，请设置 gateway.openapi.appkey、gateway.openapi.secretkey 和 gateway.openapi.url"
            );
        }
    }

    /**
     * 处理令牌 JSON 响应，优先从 Redis 缓存中获取 access_token。
     * <p>
     * 检查 Redis 中是否存在键 {@value #TOKEN_REDIS_KEY}：<br>
     * - 若存在，直接返回缓存的 access_token；<br>
     * - 若不存在，从 JSON 数据中提取 access_token，存入 Redis 并设置 1 小时过期时间，然后返回。
     *
     * @param jsonResponse 网关返回的 JSON 响应字符串，应包含 access_token / expires_in / refresh_token / re_expires_in
     * @return access_token
     * @throws IllegalArgumentException jsonResponse 为 null 或空白
     * @throws RuntimeException         JSON 解析失败或缺少 access_token 字段
     */
    public String handleTokenResponse(String jsonResponse) {
        // 1. 尝试从 Redis 缓存获取
        try {
            Object cached = redisUtils.get(TOKEN_REDIS_KEY);
            if (cached != null) {
                return (String) cached;
            }
        } catch (Exception e) {
            // Redis 异常不阻断流程，降级为从 JSON 中提取
            System.err.println("Redis read failed, fallback to JSON extraction: " + e.getMessage());
        }

        // 2. 参数校验
        if (jsonResponse == null || jsonResponse.isBlank()) {
            throw new IllegalArgumentException("jsonResponse must not be null or empty");
        }

        // 3. 解析 JSON 并提取 access_token
        JSONObject jsonObj;
        try {
            jsonObj = JSONUtil.parseObj(jsonResponse);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse gateway JSON response", e);
        }

        String accessToken = jsonObj.getStr("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("access_token not found in gateway response: " + jsonResponse);
        }

        // 4. 写入 Redis 缓存（1 小时过期）
        try {
            redisUtils.set(TOKEN_REDIS_KEY, accessToken, TOKEN_CACHE_EXPIRE_SECONDS);
        } catch (Exception e) {
            System.err.println("Redis write failed, token not cached: " + e.getMessage());
        }

        return accessToken;
    }

    /**
     * 将 Map 中的键值对以查询参数的形式填充到 URL 中。
     * 若 URL 已包含查询参数则追加（&），否则新增（?）。
     * 键和值会自动进行 URL 编码。
     *
     * @param map 待填充的参数 Map（必须非 null）
     * @param url 目标 URL 字符串（必须非 null）
     * @return 拼接查询参数后的完整 URL
     */
    public static String fillQueryParams(Map<String, String> map, String url) {
        if (map == null) {
            throw new IllegalArgumentException("map must not be null");
        }
        if (url == null) {
            throw new IllegalArgumentException("url must not be null");
        }

        StringBuilder sb = new StringBuilder(url);

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }

            sb.append(sb.indexOf("?") >= 0 ? '&' : '?');
            sb.append(encode(key));
            String value = entry.getValue();
            if (value != null) {
                sb.append('=').append(encode(value));
            }
        }

        return sb.toString();
    }

    /**
     * 对指定文本进行 URL 编码（UTF-8 字符集）。
     * 编码失败时返回原始字符串。
     */
    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return text;
        }
    }


    public HashMap getAppInfo(String userId){
        if (StringUtils.isEmpty(userId)) {
            throw new IllegalArgumentException("userId不能为空");
        }

        IdentityUser user = identityUserLookupService.getUserById(userId);

        // 1. 根据userId查询关联关系
        GatewayUserAppRela rela = gatewayUserAppRelaService.lambdaQuery()
                .eq(GatewayUserAppRela::getUserId, userId)
                .eq(GatewayUserAppRela::getIsDel, 0)
                .one();

        String appId;

        if (rela != null) {
            // 关联关系存在，直接使用已有的appId
            appId = rela.getAppId();
        } else {
            // 2. 关联关系不存在，调用创建应用（V2）接口
            Map<String, Object> createBody = new LinkedHashMap<>();
            createBody.put("application_name", user.getRealName() + "-" + userId);
            HashMap createResponse = callGatewayApi("V2CreateApplicationInfo", createBody);
            String errorMessage = (String) createResponse.getOrDefault("error_message", "");
            if (StringUtils.isNotEmpty(errorMessage)) {
                throw new RuntimeException("创建网关应用失败: " + errorMessage);
            }

            appId = (String) createResponse.getOrDefault("id", "");
            if (StringUtils.isEmpty(appId)) {
                throw new RuntimeException("创建网关应用返回ID为空");
            }

            // 3. 保存关联关系到 gateway_user_app_rela 表
            LocalDateTime now = LocalDateTime.now();
            GatewayUserAppRela gatewayUserAppRela = new GatewayUserAppRela();
            gatewayUserAppRela.setTid(NumericId.nextId());
            gatewayUserAppRela.setAppId(appId);
            gatewayUserAppRela.setUserId(userId);
            gatewayUserAppRela.setIsDel(0);
            gatewayUserAppRela.setCreatedTime(now);
            gatewayUserAppRela.setUpdatedTime(now);
            gatewayUserAppRelaService.save(gatewayUserAppRela);
        }

        // 4. 调用查询应用详情（V2）接口

        Map<String, Object> queryBody = new LinkedHashMap<>();
        queryBody.put("application_id", appId);
        queryBody.put("application_name", user.getRealName() + "-" + userId);

        HashMap queryResponse = callGatewayApi("V2QueryApplicationInfo", queryBody);
        String queryError = queryResponse.getOrDefault("error_message", "").toString();
        if (StringUtils.isNotEmpty(queryError)) {
            return new HashMap();
        }

        return queryResponse;
    }

    /**
     * 获取或创建用户的网关应用ID。
     * <p>
     * 1. 查询 gateway_user_app_rela 表获取已有 appId；<br>
     * 2. 如果不存在，调用 V2CreateApplicationInfo 创建应用并保存关联关系到 gateway_user_app_rela 表。
     *
     * @param userId 用户ID
     * @return 网关应用ID
     * @throws RuntimeException 创建应用失败时抛出
     */
    public String getOrCreateAppId(String userId) {
        String appId =  getAppInfo(userId).getOrDefault("application_id","").toString();
        if(StringUtils.isEmpty(appId)){
            return "";
        }
        return appId;
    }

    /**
     * 清除 Redis 中缓存的 access_token，使下次调用 getToken() 时强制重新获取。
     */
    private void clearTokenCache() {
        try {
            redisUtils.del(TOKEN_REDIS_KEY);
        } catch (Exception e) {
            log.warn("Failed to delete token from Redis: {}", e.getMessage());
        }
    }

    /**
     * 执行单次网关调用，返回原始响应字符串（不解析）。
     */
    private String doCallGatewayApiRaw(String action, Map<String, Object> requestBody) {
        HashMap<String, String> queryMap = new HashMap<>();
        queryMap.put("action", action);
        queryMap.put("token", getToken());
        String url = fillQueryParams(queryMap, openapiUrl);
        String response = HttpUtil.post(url, JSONUtil.toJsonStr(requestBody));
        log.info("response:{}", response);
        return response;
    }

    /**
     * 检查响应字符串是否为 access_token_invalid 错误。
     */
    private boolean isAccessTokenInvalid(String response) {
        if (StringUtils.isBlank(response) || !response.trim().startsWith("{")) {
            return false;
        }
        try {
            JSONObject json = JSONUtil.parseObj(response);
            return "access_token_invalid".equals(json.getStr("error_code"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 统一的网关 OpenAPI 调用方法（带 token 失效自动重试一次），返回 HashMap。
     * <p>
     * 适用于网关返回 JSON Object 的场景（如 V2CreateApplicationInfo、V2QueryApplicationInfo 等）。
     *
     * @param action      网关 action 名称
     * @param requestBody 请求 Body Map
     * @return 网关返回的响应 HashMap
     */
    public HashMap callGatewayApi(String action, Map<String, Object> requestBody) {
        String response = doCallGatewayApiRaw(action, requestBody);
        log.info("callGatewayApi-response:{}",response);
        if (isAccessTokenInvalid(response)) {
            log.warn("Token invalid, clearing cache and retrying...");
            clearTokenCache();
            response = doCallGatewayApiRaw(action, requestBody);
        }
        return JSONUtil.toBean(response, HashMap.class);
    }

    /**
     * 统一的网关 OpenAPI 调用方法（带 token 失效自动重试一次），返回 List。
     * <p>
     * 适用于网关返回 JSON Array 的场景（如 V2DescribeApiIdList 等）。
     *
     * @param action      网关 action 名称
     * @param requestBody 请求 Body Map
     * @return 网关返回的响应 List
     */
    public List callGatewayApiForList(String action, Map<String, Object> requestBody) {
        String response = doCallGatewayApiRaw(action, requestBody);
        if (isAccessTokenInvalid(response)) {
            log.warn("Token invalid, clearing cache and retrying...");
            clearTokenCache();
            response = doCallGatewayApiRaw(action, requestBody);
        }
        return JSONUtil.toList(response, Object.class);
    }

    /**
     * 统一的网关 OpenAPI 调用方法（带 token 失效自动重试一次），自动兼容 Object 与 Array 响应。
     * <p>
     * 根据响应 JSON 首字符判断：'{' 返回 HashMap，'[' 返回 List。
     *
     * @param action      网关 action 名称
     * @param requestBody 请求 Body Map
     * @return 网关返回的响应（HashMap 或 List）
     */
    public Object callGatewayApiForAny(String action, Map<String, Object> requestBody) {
        String response = doCallGatewayApiRaw(action, requestBody);
        if (isAccessTokenInvalid(response)) {
            log.warn("Token invalid, clearing cache and retrying...");
            clearTokenCache();
            response = doCallGatewayApiRaw(action, requestBody);
        }
        response = response.trim();
        if (response.startsWith("[")) {
            return JSONUtil.toList(response, Object.class);
        } else {
            return JSONUtil.toBean(response, HashMap.class);
        }
    }
}
