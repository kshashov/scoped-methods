package com.github.kshashov.methodscopes;

import com.github.kshashov.methodscopes.api.EnableScopedMethods;
import com.github.kshashov.methodscopes.api.ScopedMethod;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;

public class MethodScopesBeanPostProcessor implements BeanPostProcessor {
    private static final AntPathMatcher pathMatcher = new AntPathMatcher(".");
    private final MethodScopesManager scopesManager;
    private final boolean classAnnotationRequired;
    private final String[] packages;

    public MethodScopesBeanPostProcessor(MethodScopesManager methodScopesManager, boolean classAnnotationRequired, String[] packages) {
        this.scopesManager = methodScopesManager;
        this.classAnnotationRequired = classAnnotationRequired;
        this.packages = packages;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        boolean supported = true;

        // Validate class annotation
        if (classAnnotationRequired && (AnnotationUtils.findAnnotation(bean.getClass(), EnableScopedMethods.class) == null)) {
            supported = false;
        }

        if (!supported) return bean;

        // Validate package
        if (packages.length > 0) {
            supported = false;

            String beanPackage = ClassUtils.getPackageName(bean.getClass());
            for (String pattern : packages) {
                if (pathMatcher.isPattern(pattern)) {
                    supported = true;
                }
            }
        }

        if (!supported) return bean;

        // Validate methods
        supported = false;
        Method[] methods = bean.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (AnnotationUtils.findAnnotation(method, ScopedMethod.class) != null) {
                supported = true;
                break;
            }
        }

        if (!supported) return bean;

        // Create proxy
        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.addAdvice(new MethodScopesInterceptor(scopesManager));
        return proxyFactory.getProxy();
    }

    private static class MethodScopesInterceptor implements MethodInterceptor {
        private final MethodScopesManager scopesManager;

        private MethodScopesInterceptor(MethodScopesManager scopesManager) {
            this.scopesManager = scopesManager;
        }

        @Override
        public Object invoke(MethodInvocation methodInvocation) throws Throwable {
            ScopedMethod scopedMethod = methodInvocation.getMethod().getAnnotation(ScopedMethod.class);
            boolean supported = scopedMethod != null;

            if (supported) {
                scopesManager.startScope(scopedMethod.group(), scopedMethod.key());
            }

            try {
                return methodInvocation.proceed();
            } finally {
                if (supported) {
                    scopesManager.popScope(scopedMethod.group());
                }
            }
        }
    }
}