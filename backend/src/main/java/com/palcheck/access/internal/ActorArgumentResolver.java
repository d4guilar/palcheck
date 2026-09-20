package com.palcheck.access.internal;

import com.palcheck.access.api.Actor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controllers declare an {@link Actor} parameter. The actor is resolved from the authenticated principal
 * mapping exactly once per request and never from request data.
 */
@Component
class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String ATTRIBUTE = ActorArgumentResolver.class.getName() + ".actor";

    private final ActorResolver resolver;

    ActorArgumentResolver(ActorResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object cached = webRequest.getAttribute(ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        if (cached instanceof Actor actor) {
            return actor;
        }
        Actor actor = resolver.resolveCurrent();
        webRequest.setAttribute(ATTRIBUTE, actor, NativeWebRequest.SCOPE_REQUEST);
        return actor;
    }
}
