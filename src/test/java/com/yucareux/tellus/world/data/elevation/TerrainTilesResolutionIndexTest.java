package com.yucareux.tellus.world.data.elevation;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainTilesResolutionIndexTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void bundledIndexPassesStructuralValidation() {
      double resolution = TerrainTilesResolutionIndex.create().lookupResolutionMeters(27.9881, 86.9250);

      assertTrue(Double.isFinite(resolution));
      assertTrue(resolution > 0.0);
   }
}
