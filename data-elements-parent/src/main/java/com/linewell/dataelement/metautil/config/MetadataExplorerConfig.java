package com.linewell.dataelement.metautil.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 元数据探查配置类
 * 
 * @author MetaUtil
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "metadata.explorer")
public class MetadataExplorerConfig {

    /**
     * 默认抽样数量
     */
    private int defaultSampleSize = 100;

    /**
     * 最大抽样数量
     */
    private int maxSampleSize = 10000;

    /**
     * 连接超时时间(秒)
     */
    private int connectionTimeout = 30;

    /**
     * 查询超时时间(秒)
     */
    private int queryTimeout = 60;
}
