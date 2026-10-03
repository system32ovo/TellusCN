package com.yucareux.tellus.preload;

import com.google.gson.JsonElement;
import java.util.Objects;

public record TerrainPreloadManifest(
   String id,
   int formatVersion,
   long createdAtMillis,
   TerrainPreloadArea area,
   String settingsFingerprint,
   JsonElement settings,
   int downloadedChunks,
   int precreatedChunks,
   String status,
   String packageFile,
   int packageFormatVersion,
   long packageBytes,
   int packageGridStep,
   int packageGridWidth,
   int packageGridDepth
) {
   public static final int FORMAT_VERSION = 1;

   public TerrainPreloadManifest {
      id = Objects.requireNonNull(id, "id");
      area = Objects.requireNonNull(area, "area");
      settingsFingerprint = Objects.requireNonNull(settingsFingerprint, "settingsFingerprint");
      settings = Objects.requireNonNull(settings, "settings");
      status = Objects.requireNonNull(status, "status");
      packageFile = packageFile == null ? "" : packageFile;
   }
}
