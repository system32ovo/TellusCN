package com.yucareux.tellus.preload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class TerrainPreloadTextTest {
   @Test
   void localizesCanopyLoadingDetail() {
      TranslatableContents contents = translatedContents(
         TerrainPreloadText.detail("Loading 3 ETH canopy-height source tiles")
      );

      assertEquals("tellus.preload.detail.loading_canopy_height", contents.getKey());
      assertEquals("3", contents.getArgs()[0]);
   }

   @Test
   void localizesCanopyLoadedTileSource() {
      TranslatableContents contents = translatedContents(
         TerrainPreloadText.detail("Loaded ETH canopy-height tile 2/3 (13/2048/2048)")
      );

      assertEquals("tellus.preload.detail.loaded_tile", contents.getKey());
      Component source = assertInstanceOf(Component.class, contents.getArgs()[0]);
      assertEquals("tellus.preload.source.canopy_height", translatedContents(source).getKey());
   }

   @Test
   void localizesCanopySourceSummary() {
      Component summary = TerrainPreloadText.sources("ETH canopy height");

      assertFalse(summary.getSiblings().isEmpty());
      assertEquals(
         "tellus.preload.source.canopy_height",
         translatedContents(summary.getSiblings().get(0)).getKey()
      );
   }

   @Test
   void reportsCanopySourceOnlyWhenCustomTreesAreEnabled() {
      assertTrue(TerrainPreloadJob.sourceDetail(EarthGeneratorSettings.DEFAULT).contains("ETH canopy height"));
      assertFalse(
         TerrainPreloadJob.sourceDetail(EarthGeneratorSettings.DEFAULT.withCustomTrees(false)).contains("ETH canopy height")
      );
   }

   private static TranslatableContents translatedContents(Component component) {
      return assertInstanceOf(TranslatableContents.class, component.getContents());
   }
}
