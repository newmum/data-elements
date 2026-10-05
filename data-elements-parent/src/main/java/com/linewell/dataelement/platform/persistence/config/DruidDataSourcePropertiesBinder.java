package com.linewell.dataelement.platform.persistence.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.wall.WallFilter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Locale;

/**
 * Binds Druid-specific settings when a generic Spring datasource creates Druid.
 */
@Component
public class DruidDataSourcePropertiesBinder implements BeanPostProcessor {

    private final Environment environment;

    public DruidDataSourcePropertiesBinder(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName)
            throws BeansException {
        if (bean instanceof DruidDataSource dataSource) {
            Binder.get(environment).bind(
                    "spring.datasource.druid",
                    Bindable.ofInstance(dataSource)
            );
            applyMySqlNetworkTimeouts(dataSource);
            if (isDameng(dataSource)) {
                dataSource.getProxyFilters().removeIf(WallFilter.class::isInstance);
            }
        }
        return bean;
    }

    /** Keep a dropped MySQL connection from holding login and tenant queries for a minute. */
    public static void applyMySqlNetworkTimeouts(DruidDataSource dataSource) {
        String url = dataSource.getUrl();
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql:")) {
            return;
        }
        dataSource.setConnectTimeout(cappedTimeout(dataSource.getConnectTimeout(), 5_000));
        dataSource.setSocketTimeout(cappedTimeout(dataSource.getSocketTimeout(), 30_000));
        dataSource.setMaxWait(cappedTimeout(dataSource.getMaxWait(), 10_000));
    }

    private static int cappedTimeout(long configured, int maximum) {
        return configured > 0 && configured < maximum ? (int) configured : maximum;
    }

    private boolean isDameng(DruidDataSource dataSource) {
        String url = dataSource.getUrl();
        return url != null && url.toLowerCase(java.util.Locale.ROOT).startsWith("jdbc:dm:");
    }
}
