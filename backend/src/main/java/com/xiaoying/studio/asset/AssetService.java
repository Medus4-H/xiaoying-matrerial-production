package com.xiaoying.studio.asset;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.common.InternalErrorDetail;
import com.xiaoying.studio.common.SafeLog;
import com.xiaoying.studio.identity.User;
import com.xiaoying.studio.storage.LocalFileStorage;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetService {
  private static final Logger log = LoggerFactory.getLogger(AssetService.class);
  private final AssetMapper mapper;
  private final AssetValidator validator;
  private final LocalFileStorage storage;
  private final Clock clock;

  public AssetService(
      AssetMapper mapper, AssetValidator validator, LocalFileStorage storage, Clock clock) {
    this.mapper = mapper;
    this.validator = validator;
    this.storage = storage;
    this.clock = clock;
  }

  public AssetResponse upload(User user, MultipartFile file) throws IOException {
    var image = validator.validate(file);
    String id = UUID.randomUUID().toString();
    String key = storage.save(id, image.mimeType(), image.bytes());
    String original = safeName(file.getOriginalFilename());
    var asset =
        new Asset(
            id,
            user.id(),
            key,
            original,
            image.mimeType(),
            image.bytes().length,
            image.width(),
            image.height(),
            clock.instant());
    try {
      mapper.insert(asset);
    } catch (RuntimeException e) {
      try {
        storage.delete(key);
      } catch (IOException cleanup) {
        e.addSuppressed(cleanup);
      }
      throw e;
    }
    SafeLog.write(
        () ->
            log.atInfo()
                .addKeyValue("event", "asset.upload.stored")
                .addKeyValue("assetId", id)
                .addKeyValue("mime", asset.mimeType())
                .addKeyValue("sizeBytes", asset.sizeBytes())
                .addKeyValue("width", asset.width())
                .addKeyValue("height", asset.height())
                .log("Asset uploaded"));
    return new AssetResponse(
        id,
        "/api/assets/" + id + "/content",
        asset.mimeType(),
        asset.sizeBytes(),
        asset.width(),
        asset.height());
  }

  public List<Asset> requireOwnedAssets(
      User user, List<String> ids, TemplateRequirements requirements) throws IOException {
    if (ids == null
        || requirements == null
        || ids.size() != requirements.requiredImageCount()
        || ids.stream().anyMatch(java.util.Objects::isNull)
        || new HashSet<>(ids).size() != ids.size()) {
      throw new ApiException(ErrorKey.ASSET_COUNT);
    }
    List<Asset> result = new ArrayList<>(ids.size());
    for (String id : ids) {
      Asset asset = requireOwned(user, id);
      if (!requirements.acceptedMimeTypes().contains(asset.mimeType())
          || asset.sizeBytes() > requirements.maxFileSizeBytes()) {
        throw new ApiException(ErrorKey.ASSET_REQUIREMENTS);
      }
      content(asset);
      result.add(asset);
    }
    return List.copyOf(result);
  }

  public Asset requireOwned(User user, String id) {
    if (id == null || !id.matches("[0-9a-f-]{36}"))
      throw notFound(InternalErrorDetail.ASSET_ID_INVALID);
    Asset asset = mapper.findOwned(id, user.id());
    if (asset == null) throw notFound(InternalErrorDetail.ASSET_UNAVAILABLE_OR_NOT_OWNED);
    return asset;
  }

  public Resource content(Asset asset) throws IOException {
    try {
      return storage.read(asset.storageKey());
    } catch (java.nio.file.NoSuchFileException e) {
      throw notFound(InternalErrorDetail.ASSET_FILE_MISSING);
    }
  }

  private ApiException notFound(InternalErrorDetail detail) {
    return new ApiException(ErrorKey.ASSET_NOT_FOUND, detail);
  }

  private String safeName(String value) {
    if (value == null) return "image";
    String name = value.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
    if (name.isEmpty()) return "image";
    return name.length() > 255 ? name.substring(0, 255) : name;
  }

  public record AssetResponse(
      String id, String previewUrl, String mimeType, long sizeBytes, int width, int height) {}
}
