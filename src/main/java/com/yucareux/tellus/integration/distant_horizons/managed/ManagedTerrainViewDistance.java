package com.yucareux.tellus.integration.distant_horizons.managed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ManagedTerrainViewDistance {
   private static final int FALLBACK_RADIUS_CHUNKS = 128;

   private ManagedTerrainViewDistance() {
   }

   public static int detect() {
      try {
         Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
         Field configsField = delayed.getField("configs");
         Object configs = configsField.get(null);
         if (configs == null) {
            return FALLBACK_RADIUS_CHUNKS;
         }
         Method graphicsMethod = configs.getClass().getMethod("graphics");
         Object graphics = graphicsMethod.invoke(configs);
         Object configValue = graphics.getClass().getMethod("chunkRenderDistance").invoke(graphics);
         Object value = configValue.getClass().getMethod("getValue").invoke(configValue);
         return value instanceof Number number
            ? Math.max(ManagedTerrainTarget.MIN_RENDER_RADIUS_CHUNKS, Math.min(ManagedTerrainTarget.MAX_RENDER_RADIUS_CHUNKS, number.intValue()))
            : FALLBACK_RADIUS_CHUNKS;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
         return FALLBACK_RADIUS_CHUNKS;
      }
   }
}
