package com.example.springmvccontroller.config;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/**
 * Intercepts all SQL statements executed through the application DataSource
 * (JPA/Hibernate, JdbcTemplate, or raw JDBC). Works with any database (H2, SQL Server, etc.).
 * <p>
 * Enable with: {@code sql.intercept.enabled=true} (default: true). Set to false to disable.
 */
@Configuration
public class SqlInterceptorConfig {

    private static final Logger log = LoggerFactory.getLogger("SQL_INTERCEPT");

    @Bean
    @ConditionalOnProperty(name = "sql.intercept.enabled", havingValue = "true", matchIfMissing = true)
    public static BeanPostProcessor dataSourceProxyPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof DataSource && !isProxy(bean)) {
                    return ProxyDataSourceBuilder.create((DataSource) bean)
                            .name("App")
                            .listener(new SqlLoggingListener())
                            .build();
                }
                return bean;
            }

            private boolean isProxy(Object bean) {
                return bean.getClass().getSimpleName().contains("ProxyDataSource");
            }
        };
    }

    /**
     * Logs every executed SQL statement (and batch queries). Replace or extend this listener
     * to send to a monitoring system, collect metrics, or filter by query type.
     */
    public static class SqlLoggingListener implements QueryExecutionListener {

        @Override
        public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
            // no-op
        }

        @Override
        public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
            for (QueryInfo qi : queryInfoList) {
                String sql = qi.getQuery();
                if (sql != null && !sql.isBlank()) {
                    log.info("SQL: {}", sql.trim().replaceAll("\\s+", " "));
                }
            }
        }
    }
}
