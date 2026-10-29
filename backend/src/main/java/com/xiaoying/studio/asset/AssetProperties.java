package com.xiaoying.studio.asset;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("studio.assets")
public record AssetProperties(long maxBytes, int maxDimension, long maxPixels) {
  public AssetProperties {
    if (maxBytes <= 0 || maxDimension <= 0 || maxPixels <= 0) {
      throw new IllegalArgumentException("Invalid asset limits");
    }
  }
}
