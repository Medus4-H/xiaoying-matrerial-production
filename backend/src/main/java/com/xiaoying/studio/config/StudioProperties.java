package com.xiaoying.studio.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "studio")
public record StudioProperties(Path storageRoot, boolean simulationEnabled) {}
