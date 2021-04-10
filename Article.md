## Method related scopes with Spring Boot

One day I needed to support an `@UseReplica` annotation to indicate my `RoutingDatasource` that _replica_ datasource should be used for the annotated method.
That task was easily solved with a custom AspectJ interceptor. 
However, having encountered a similar problem after, I decided to introduce support for method scopes in a simple library.

In this article we'll take a look at how such a library could be implemented.

### Usage
Let's imagine we have `@ScopedMethod` annotation that only includes scope identifier as a value and could be used as follows:
```java
public interface MyService {
    @ScopedMethod("master")
    void update(String name);

    @ScopedMethod("slave")
    String get(String name);
}
```

Then, somewhere in the `AbstractRoutingDataSource` implementation, we could have something like this:
```java
public class MasterSlaveDataSource extends AbstractRoutingDataSource {
    ...
            
    @Override
    protected Object determineCurrentLookupKey() {
        return scopesManager.getCurrent();
    }
}
```

As you can see, our scope only contains its own identifier. If we need to store some additional data, we could create additional storage from which we would get everything we need by the scope identifier.

### Implementation details

If we abstract from the original goal, we just need the following abilites:
* to declare the scope in which the method should be executed
* to get the identifier of the current scope (if any) at any time
* to invoke scoped methods from each other

First of all, we need `@ScopedMethod` annotation:
```java
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface ScopedMethod {
    String value() default "";
}
```

We figured out the first step, let's move on to the second. We need a separate service in which we would register the scope when entering it, and delete it upon exiting. And let's not forget the method for getting the current scope.

Let's imagine that we are in 'master' scope, and at the same time we are entering in an internal 'slave' scope. The we finished with the internal scope, which means we must return to the first 'master' scope. Doesn't it look like LIFO? 
Yes, our service will be just a stack wrapper. 

```java
@Service
public class MethodScopesManager {
    private static final ThreadLocal<Stack<String>> ACTIVE_SCOPES = new ThreadLocal<>();

    public MethodScopesManager() {
        ACTIVE_SCOPES.set(new Stack<>());
    }

    public String getCurrent() {
        Stack<String> scopes = ACTIVE_SCOPES.get();
        if (scopes.empty()) {
            return null;
        }

        return scopes.peek();
    }

    void startScope(@NotNull String key) {
        ACTIVE_SCOPES.get().add(key);
    }

    void popScope() {
        ACTIVE_SCOPES.get().pop();
    }
}
```

It is also important to note that we are using `ThreadLocal`, so our scopes **will not work correctly** if annotated methods spawn new threads.

We're almost there. The only thing we need to do is to create `BeanPostProcessor`:
```java
public class MethodScopesBeanPostProcessor2 implements BeanPostProcessor {
    ...        

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // Validate methods
        boolean supported = false;
        Method[] methods = bean.getClass().getDeclaredMethods();
        for (Method method: methods) {
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
        ...
    }
}
```
Here we make sure that the current bean contains annotated methods and create a proxy for it.

In our interceptor we will
* register the scope (if annotation exists)
* invoke the original method
* remove the scope (if annotation exists)

Let's take a look at the possible implementation:
```java
    private static class MethodScopesInterceptor implements MethodInterceptor {
        ...

        @Override
        public Object invoke(MethodInvocation methodInvocation) throws Throwable {
            ScopedMethod scopedMethod = methodInvocation.getMethod().getAnnotation(ScopedMethod.class);
            boolean supported = scopedMethod != null;

            if (supported) {
                scopesManager.startScope(scopedMethod.key());
            }

            try {
                return methodInvocation.proceed();
            } finally {
                if (supported) {
                    scopesManager.popScope();
                }
            }
        }
    }
```