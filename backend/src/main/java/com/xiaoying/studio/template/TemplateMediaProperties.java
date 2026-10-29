package com.xiaoying.studio.template;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("studio.media")
public record TemplateMediaProperties(String root) {}
