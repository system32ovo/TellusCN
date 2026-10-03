package com.yucareux.tellus.client.hud;

import com.yucareux.tellus.integration.distant_horizons.managed.ManagedTerrainClientState;
import com.yucareux.tellus.integration.distant_horizons.managed.ManagedTerrainDownloadStatus;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ManagedTerrainDownloadOverlay {
   private static final int MAX_WIDTH = 300;

   private ManagedTerrainDownloadOverlay() {
   }

   public static void render(GuiGraphicsExtractor graphics) {
      ManagedTerrainDownloadStatus status = ManagedTerrainClientState.visibleStatus();
      if (status == null) return;
      int width = Math.min(MAX_WIDTH, graphics.guiWidth() - 16);
      if (width < 120) return;

      Font font = Minecraft.getInstance().font;
      int x = (graphics.guiWidth() - width) / 2;
      int y = 8;
      int accent = accent(status.stage());
      String stage = Component.translatable("tellus.managed_terrain.overlay.stage." + status.stage().name().toLowerCase(Locale.ROOT)).getString();
      String title = Component.translatable("tellus.managed_terrain.overlay.title").getString() + " · " + stage;
      if (status.totalCells() > 0) title += " · " + Math.round(status.progress() * 100.0) + "%";

      graphics.fill(x, y, x + width, y + 28, 0xCC10151B);
      graphics.fill(x, y, x + 3, y + 28, accent);
      graphics.text(font, fit(font, title, width - 12), x + 7, y + 4, accent, false);
      graphics.text(font, fit(font, detail(status), width - 12), x + 7, y + 15, 0xFFD0D7DE, false);
      graphics.fill(x + 3, y + 25, x + width, y + 28, 0xFF26313A);
      int progressWidth = (int)Math.round((width - 3) * status.progress());
      if (progressWidth > 0) graphics.fill(x + 3, y + 25, x + 3 + progressWidth, y + 28, accent);
   }

   private static String detail(ManagedTerrainDownloadStatus status) {
      if (status.stage() == ManagedTerrainDownloadStatus.Stage.COMPATIBILITY_FALLBACK) {
         return Component.translatable("tellus.managed_terrain.overlay.compatibility_fallback").getString();
      }
      if (status.stage() == ManagedTerrainDownloadStatus.Stage.FAILED) {
         return Component.translatable("tellus.managed_terrain.overlay.failed_detail", status.failedCells()).getString();
      }
      return Component.translatable(
         "tellus.managed_terrain.overlay.progress", status.completedCells(), status.totalCells(), status.renderRadiusChunks(), status.safetyRingChunks()
      ).getString();
   }

   private static String fit(Font font, String text, int width) {
      return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
   }

   private static int accent(ManagedTerrainDownloadStatus.Stage stage) {
      return switch (stage) {
         case COMPLETE -> 0xFF55D187;
         case DEGRADED -> 0xFFFFCA45;
         case FAILED -> 0xFFFF6565;
         case COMPATIBILITY_FALLBACK -> 0xFFFFA94D;
         default -> 0xFF45C3F2;
      };
   }
}
