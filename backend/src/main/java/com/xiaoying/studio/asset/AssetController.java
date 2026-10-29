package com.xiaoying.studio.asset;

import com.xiaoying.studio.identity.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
  private final AssetService assets;
  private final RequestIdentity identity;

  public AssetController(AssetService assets, RequestIdentity identity) {
    this.assets = assets;
    this.identity = identity;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AssetService.AssetResponse> upload(
      @RequestPart("file") MultipartFile file, HttpServletRequest request) throws IOException {
    identity.checkOrigin(request);
    var user = identity.resolve(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(assets.upload(user, file));
  }

  @GetMapping("/{id}/content")
  public ResponseEntity<Resource> content(@PathVariable String id, HttpServletRequest request)
      throws IOException {
    var asset = assets.requireOwned(identity.resolve(request), id);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(asset.mimeType()))
        .contentLength(asset.sizeBytes())
        .body(assets.content(asset));
  }
}
