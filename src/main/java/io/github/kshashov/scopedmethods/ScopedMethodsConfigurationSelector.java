package io.github.kshashov.scopedmethods;

import io.github.kshashov.scopedmethods.api.EnableScopedMethods;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.context.annotation.AutoProxyRegistrar;

/**
 * Selects which {@link BaseScopedMethodConfiguration} subclass should be used based on the value of AdviceMode mode on the importing @Configuration class.
 */
public class ScopedMethodsConfigurationSelector extends AdviceModeImportSelector<EnableScopedMethods> {
    @Override
    protected String[] selectImports(AdviceMode adviceMode) {
        switch (adviceMode) {
            case PROXY:
                return new String[]{AutoProxyRegistrar.class.getName(), ProxyScopedMethodsConfiguration.class.getName()};
            case ASPECTJ:
                return new String[]{AspectScopedMethodsConfiguration.class.getName()};
            default:
                return null;
        }
    }
}
