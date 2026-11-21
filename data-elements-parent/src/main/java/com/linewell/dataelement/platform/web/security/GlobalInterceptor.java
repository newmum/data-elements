package com.linewell.dataelement.platform.web.security;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.integration.workflow.WarmFlowDesignerAccess;
import com.linewell.dataelement.magic.runtime.result.MagicApiResultProvider;
import com.linewell.dataelement.magic.runtime.route.PublishedFlowRoutes;
import com.linewell.dataelement.magic.runtime.route.RegisteredMagicFunctionInvoker;
import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.model.common.StatusCodeEnum;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import com.linewell.dataelement.platform.tenant.api.IamMfaMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.protocol.IdentityApiMagicModule;
import com.linewell.dataelement.platform.tenant.protocol.IdentityReceiverAccess;
import com.linewell.dataelement.platform.tenant.protocol.LegacySsoAccess;
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.RequestInterceptor;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.core.service.impl.RequestMagicDynamicRegistry;
import org.ssssssss.magicapi.core.web.RequestHandler;
import org.ssssssss.magicapi.servlet.jakarta.MagicJakartaHttpServletRequest;
import org.ssssssss.script.runtime.ExitValue;

@Component
public class GlobalInterceptor implements HandlerInterceptor, RequestInterceptor {
    private static final String MATCHED_ACCESS = GlobalInterceptor.class.getName() + ".matchedAccess";
    private static final String MATCHED_RESOURCE = GlobalInterceptor.class.getName() + ".matchedResource";
    private static final String VERIFIED_ACCESS = GlobalInterceptor.class.getName() + ".verifiedAccess";
    private static final String MATCHED_PUBLISHED_FLOW = GlobalInterceptor.class.getName() + ".matchedPublishedFlow";
    private static final String PUBLISHED_EXECUTION = GlobalInterceptor.class.getName() + ".publishedExecution";
    private static final String IDENTITY_PROOF = GlobalInterceptor.class.getName() + ".identityProof";
    private static final String IDENTITY_GROUP_ID = "1ae52a0ba8565e9d820f00dec5d199af";
    private static final String AUTH_ACCOUNT_CHECK = "4d54c6e215625d9ca6220ca6477b5d6c";
    private static final String PLATFORM_ACCOUNT_CHECK = "c6d4f90ef64c535784d7f1df4030c92f";
    private static final String API_REQUEST_SOURCE_CHECK = "bf142250e1a35995a1041adb40ea5bf4";

    private final WhiteListProp whiteList;
    private final PlatformSessionMagicModule platformSession;
    private final ObjectProvider<RequestMagicDynamicRegistry> registry;
    private final ObjectProvider<MagicResourceService> resources;
    private final GlobalExceptionHandler errors;
    private final ObjectProvider<RegisteredMagicFunctionInvoker> functions;
    private final ObjectProvider<IamMfaMagicModule> mfa;
    private final ObjectProvider<IdentityApiMagicModule> identityApi;
    private final ObjectProvider<IdentityReceiverAccess> identityReceiver;
    private final ObjectProvider<LegacySsoAccess> legacySso;
    private final PublishedFlowRoutes publishedFlows;
    private final MagicApiResultProvider magicResults;
    private final WarmFlowDesignerAccess workflowDesigner;

    public GlobalInterceptor(WhiteListProp whiteList, PlatformSessionMagicModule platformSession,
                             ObjectProvider<RequestMagicDynamicRegistry> registry, ObjectProvider<MagicResourceService> resources,
                             GlobalExceptionHandler errors,
                             ObjectProvider<RegisteredMagicFunctionInvoker> functions,
                             ObjectProvider<IamMfaMagicModule> mfa,
                             ObjectProvider<IdentityApiMagicModule> identityApi,
                             ObjectProvider<IdentityReceiverAccess> identityReceiver,
                             ObjectProvider<LegacySsoAccess> legacySso, PublishedFlowRoutes publishedFlows,
                             MagicApiResultProvider magicResults, WarmFlowDesignerAccess workflowDesigner) {
        this.whiteList = whiteList;
        this.platformSession = platformSession;
        this.registry = registry;
        this.resources = resources;
        this.errors = errors;
        this.functions = functions;
        this.mfa = mfa;
        this.identityApi = identityApi;
        this.identityReceiver = identityReceiver;
        this.legacySso = legacySso;
        this.publishedFlows = publishedFlows;
        this.magicResults = magicResults;
        this.workflowDesigner = workflowDesigner;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.removeAttribute(VERIFIED_ACCESS);
        request.removeAttribute(MATCHED_PUBLISHED_FLOW);
        if (handler instanceof HandlerMethod method && method.getBeanType().isAnnotationPresent(ProtocolAccess.class)) {
            return true;
        }
        if (workflowDesigner.matches(handler)) {
            return true;
        }
        AccessMatch match = match(request, handler);
        request.removeAttribute(MATCHED_ACCESS);
        request.removeAttribute(MATCHED_RESOURCE);
        if (match != null && match.publishedFlow() != null) {
            request.setAttribute(MATCHED_PUBLISHED_FLOW, match.publishedFlow());
        }
        authorize(request, match);
        if (match != null) {
            request.setAttribute(VERIFIED_ACCESS, match);
        }
        return true;
    }

    public MagicAccessPolicy matchedMagicAccess(HttpServletRequest request, Object handler) {
        AccessMatch match = match(request, handler);
        return match == null ? null : match.policy();
    }

    public ApiInfo matchedMagicResource(HttpServletRequest request, Object handler) {
        return match(request, handler) != null && request.getAttribute(MATCHED_RESOURCE) instanceof ApiInfo api
                ? api : null;
    }

    public boolean matchedIdentityGroup(HttpServletRequest request, Object handler) {
        AccessMatch match = match(request, handler);
        return match != null && match.identityGroup();
    }

    private AccessMatch match(HttpServletRequest request, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !RequestHandler.class.isAssignableFrom(method.getBeanType())) {
            return null;
        }
        if (request.getAttribute(MATCHED_ACCESS) instanceof AccessMatch match) {
            return match;
        }
        var api = registry.getObject().getApiInfoFromRequest(new MagicJakartaHttpServletRequest(request, null));
        var match = declared(api);
        request.setAttribute(MATCHED_RESOURCE, api);
        request.setAttribute(MATCHED_ACCESS, match);
        return match;
    }

    @Override
    public Object preHandle(RequestEntity entity) {
        try {
            if (entity.getRequest() != null && entity.getRequest().getRequest() instanceof HttpServletRequest request) {
                request.removeAttribute(PUBLISHED_EXECUTION);
                request.removeAttribute(IDENTITY_PROOF);
                // 热刷新可能在 MVC 通过后变更实际执行的资源，切换访问类型时重新校验。
                AccessMatch execution = declared(entity.getApiInfo());
                MagicAccessPolicy access = execution.policy();
                AccessMatch prior = request.getAttribute(VERIFIED_ACCESS) instanceof AccessMatch result
                        ? result : null;
                if ((prior != null || access != null || execution.identityGroup() || execution.publishedFlow() != null)
                        && !execution.equals(prior)) {
                    authorize(request, execution);
                    // 显式策略必须经 MVC 绑定数据源和租户。缺少绑定或策略变化时要求重新请求。
                    throw new BizException(StatusCodeEnum.FORBIDDEN, prior == null
                            ? "接口访问策略未在入口校验" : "接口访问策略已变更，请重新请求");
                }
                if (access != null) {
                    Object denial = switch (access) {
                        case IDAAS_ACCOUNT_SESSION -> verifyIdentityContext(request, entity, access, true);
                        case MFA_SESSION -> verifyIdentityContext(request, entity, access,
                                "auth-account".equals(bodyField(entity, "realm")));
                        case MFA_LOGIN_CHALLENGE -> verifyMfaChallenge(request, entity);
                        case TENANT_SESSION -> verifyTenantSession(execution, entity);
                        case IDAAS_API_BEARER -> verifyBearer(request, entity);
                        case IDAAS_RECEIVER_SIGNATURE -> verifyReceiver(request, entity);
                        case LEGACY_SSO_TICKET, LEGACY_SSO_REDEEM -> verifyLegacySso(request, entity, access);
                        default -> null;
                    };
                    if (denial != null) {
                        return denial;
                    }
                }
                if (execution.publishedFlow() != null) {
                    request.setAttribute(PUBLISHED_EXECUTION, entity);
                }
            }
        } catch (BizException error) {
            // 脚本执行前终止并保留 MVC 的业务错误信封。
            return ResponseEntity.ok(errors.handleBizException(error));
        }
        return null;
    }

    private Object verifyTenantSession(AccessMatch match, RequestEntity entity) {
        String message = match.sessionRequiredMessage();
        return message != null && !StpUtil.isLogin()
                ? ResponseEntity.ok(magicResults.buildResult(entity, 401, message, null)) : null;
    }

    private Object verifyReceiver(HttpServletRequest request, RequestEntity entity) {
        try {
            Map<String, Object> verified = identityReceiver.getObject().authorize(entity.getRequestBody());
            request.setAttribute(IDENTITY_PROOF, new IdentityProof(MagicAccessPolicy.IDAAS_RECEIVER_SIGNATURE, verified));
            return null;
        } catch (BizException rejected) {
            return ResponseEntity.ok(magicResults.buildResult(entity, rejected.getCode(), rejected.getMsg(), null));
        } catch (Exception error) {
            return ResponseEntity.ok(magicResults.buildException(entity, error));
        }
    }

    private Object verifyLegacySso(HttpServletRequest request, RequestEntity entity, MagicAccessPolicy policy) {
        try {
            var upstream = legacySso.getObject();
            var proof = policy == MagicAccessPolicy.LEGACY_SSO_TICKET ? upstream.authorizeTicket()
                    : upstream.authorizeRedemption(entity.getParameters());
            request.setAttribute(IDENTITY_PROOF, new IdentityProof(policy, proof));
            return null;
        } catch (BizException rejected) {
            return ResponseEntity.ok(magicResults.buildResult(entity, rejected.getCode(), rejected.getMsg(), null));
        } catch (RuntimeException error) {
            return ResponseEntity.ok(magicResults.buildException(entity, error));
        }
    }

    private Object verifyBearer(HttpServletRequest request, RequestEntity entity) {
        try (var control = TenantContext.control()) {
            Object result = functions.getObject().invoke(API_REQUEST_SOURCE_CHECK,
                    Map.of("checkRequestSource", true));
            if (result instanceof ExitValue exit) {
                Object[] values = exit.getValues();
                if (values.length == 2 && values[0] instanceof Number code
                        && (code.intValue() == 403 || code.intValue() == 429) && values[1] instanceof String message) {
                    return ResponseEntity.ok(magicResults.buildResult(entity, code.intValue(), message, null));
                }
            }
            if (!Boolean.TRUE.equals(result)) {
                throw new IllegalStateException("接口来源与配额校验返回无效结果");
            }
            Map<String, Object> caller = identityApi.getObject().authorize(request);
            request.setAttribute(IDENTITY_PROOF, new IdentityProof(MagicAccessPolicy.IDAAS_API_BEARER, caller));
            return null;
        } catch (AccessDeniedException denied) {
            return ResponseEntity.ok(magicResults.buildResult(entity, 403, "访问令牌或当前接口授权无效", null));
        } catch (RuntimeException error) {
            return ResponseEntity.ok(magicResults.buildException(entity, error));
        }
    }

    private Object verifyIdentityContext(HttpServletRequest request, RequestEntity entity,
                                         MagicAccessPolicy policy, boolean authAccount) {
        Object result;
        try (var control = TenantContext.control()) {
            result = functions.getObject().invoke(authAccount ? AUTH_ACCOUNT_CHECK : PLATFORM_ACCOUNT_CHECK, Map.of());
        }
        if (result instanceof ExitValue exit) {
            Object[] values = exit.getValues();
            if (values.length == 2 && values[0] instanceof Number code && code.intValue() == 401
                    && values[1] instanceof String message) {
                return ResponseEntity.ok(magicResults.buildResult(entity, 401, message, null));
            }
        }
        if (!(result instanceof Map<?, ?> context) || authAccount && !"AUTH_ACCOUNT".equals(context.get("actorKind"))
                || !(context.get("account") instanceof Map<?, ?>) || context.get("userId") == null) {
            throw new IllegalStateException("身份会话校验返回无效结果");
        }
        Map<?, ?> proof = policy == MagicAccessPolicy.MFA_SESSION
                ? Map.of("kind", authAccount ? "AUTH_ACCOUNT" : "OPERATOR", "id", context.get("userId"), "context", context)
                : context;
        request.setAttribute(IDENTITY_PROOF, new IdentityProof(policy, proof));
        return null;
    }

    private Object verifyMfaChallenge(HttpServletRequest request, RequestEntity entity) {
        Object value = bodyField(entity, "challenge");
        var ticket = mfa.getObject().attempt(value instanceof String challenge ? challenge : null, "LOGIN");
        if (ticket.get("id") == null || !(ticket.get("version") instanceof Number)
                || !("OPERATOR".equals(ticket.get("kind")) || "AUTH_ACCOUNT".equals(ticket.get("kind")))) {
            return ResponseEntity.ok(magicResults.buildResult(entity, 401, "认证挑战已失效，请重新登录", null));
        }
        request.setAttribute(IDENTITY_PROOF, new IdentityProof(MagicAccessPolicy.MFA_LOGIN_CHALLENGE, ticket));
        return null;
    }

    private static Object bodyField(RequestEntity entity, String field) {
        return entity.getRequestBody() instanceof Map<?, ?> body ? body.get(field) : null;
    }

    public static Map<?, ?> verifiedAuthAccountContext(HttpServletRequest request) {
        return verifiedIdentityProof(request, MagicAccessPolicy.IDAAS_ACCOUNT_SESSION);
    }

    public static Map<?, ?> verifiedIdentityProof(HttpServletRequest request, MagicAccessPolicy policy) {
        if (request != null && request.getAttribute(IDENTITY_PROOF) instanceof IdentityProof proof
                && proof.policy() == policy) {
            return proof.data();
        }
        throw new IllegalStateException("身份凭证尚未在当前 Magic 入口校验");
    }

    private AccessMatch declared(ApiInfo api) {
        try {
            MagicAccessPolicy policy = MagicAccessPolicy.declared(api);
            return new AccessMatch(policy, MagicAccessPolicy.sessionRequiredMessage(api, policy), publishedFlows.match(api), inIdentityGroup(api));
        } catch (IllegalArgumentException error) {
            throw new BizException(StatusCodeEnum.FORBIDDEN, "接口访问策略配置无效");
        }
    }

    private boolean inIdentityGroup(ApiInfo api) {
        if (api == null || api.getGroupId() == null || api.getGroupId().isBlank()) {
            return false;
        }
        MagicResourceService tree = resources.getObject();
        var visited = new HashSet<String>();
        String groupId = api.getGroupId();
        while (groupId != null && !"0".equals(groupId) && visited.add(groupId)) {
            var group = tree.getGroup(groupId);
            if (group == null) {
                return false;
            }
            if (IDENTITY_GROUP_ID.equals(groupId) && "0".equals(group.getParentId())) {
                return true;
            }
            groupId = group.getParentId();
        }
        return false;
    }

    private void authorize(HttpServletRequest request, AccessMatch match) {
        MagicAccessPolicy access = match == null ? null : match.policy();
        String path = request.getRequestURI();
        boolean allowed = access == null
                ? whiteList.isWhiteListed(path) || (match != null && match.identityGroup() ? platformSession.isLogin() : StpUtil.isLogin())
                : switch (access) {
                    // Account realm and challenge validation are completed before the Magic business script executes.
                    case PUBLIC, PUBLIC_ACTION, SESSION_LOGIN, SESSION_LOGOUT, IDAAS_ACCOUNT_SESSION, IDAAS_API_BEARER,
                            IDAAS_RECEIVER_SIGNATURE, LEGACY_SSO_TICKET, LEGACY_SSO_REDEEM,
                            MFA_SESSION, MFA_LOGIN_CHALLENGE -> true;
                    // Resources that keep a Magic 401 envelope validate the session before script execution.
                    case TENANT_SESSION -> match.sessionRequiredMessage() != null || StpUtil.isLogin();
                    case IDAAS_PLATFORM_SESSION -> platformSession.isLogin();
                };
        if (!allowed) {
            throw new BizException(StatusCodeEnum.BASELINE_NOLOGIN, "未登录或登录已过期");
        }
    }

    public static MagicAccessPolicy verifiedMagicAccess(HttpServletRequest request) {
        return request.getAttribute(VERIFIED_ACCESS) instanceof AccessMatch prior
                ? prior.policy() : null;
    }

    public static boolean verifiedIdentityGroup(HttpServletRequest request) {
        return request.getAttribute(VERIFIED_ACCESS) instanceof AccessMatch prior && prior.identityGroup();
    }

    public static PublishedFlowRoutes.Match verifiedPublishedFlow(HttpServletRequest request) {
        return request.getAttribute(VERIFIED_ACCESS) instanceof AccessMatch prior ? prior.publishedFlow() : null;
    }

    public static boolean matchedPublishedFlow(HttpServletRequest request) {
        return request.getAttribute(MATCHED_PUBLISHED_FLOW) != null;
    }

    public static boolean isPublishedFlowExecution(RequestEntity entity) {
        return entity != null && entity.getRequest() != null
                && entity.getRequest().getRequest() instanceof HttpServletRequest request
                && request.getAttribute(PUBLISHED_EXECUTION) == entity;
    }

    private record AccessMatch(MagicAccessPolicy policy, String sessionRequiredMessage,
                               PublishedFlowRoutes.Match publishedFlow, boolean identityGroup) { }
    private record IdentityProof(MagicAccessPolicy policy, Map<?, ?> data) { }
}
