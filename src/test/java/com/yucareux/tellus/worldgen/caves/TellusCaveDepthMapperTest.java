package com.yucareux.tellus.worldgen.caves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TellusCaveDepthMapperTest {
   @Test
   void preservesVanillaDepthsForASeaLevelColumn() {
      assertEquals(
         0,
         TellusCaveDepthMapper.virtualYForActualY(
            0,
            TellusCaveDepthMapper.VANILLA_SEA_LEVEL,
            TellusCaveDepthMapper.VANILLA_MIN_Y,
            TellusCaveDepthMapper.VANILLA_SEA_LEVEL,
            TellusCaveDepthMapper.VANILLA_MIN_Y
         )
      );
      assertEquals(
         0,
         TellusCaveDepthMapper.actualYForVirtualFeature(
            0,
            TellusCaveDepthMapper.VANILLA_SEA_LEVEL,
            TellusCaveDepthMapper.VANILLA_SEA_LEVEL,
            TellusCaveDepthMapper.VANILLA_MIN_Y
         )
      );
   }

   @Test
   void compressesTheFullVanillaProfileIntoAHighTerrainShell() {
      int surfaceY = 1_000;
      int bottomY = 873;

      assertEquals(
         TellusCaveDepthMapper.VANILLA_MAX_Y,
         TellusCaveDepthMapper.virtualYForActualY(
            surfaceY,
            surfaceY,
            bottomY,
            TellusCaveDepthMapper.VANILLA_MAX_Y,
            TellusCaveDepthMapper.VANILLA_MIN_Y
         )
      );
      assertEquals(
         TellusCaveDepthMapper.VANILLA_MIN_Y,
         TellusCaveDepthMapper.virtualYForActualY(
            bottomY,
            surfaceY,
            bottomY,
            TellusCaveDepthMapper.VANILLA_MAX_Y,
            TellusCaveDepthMapper.VANILLA_MIN_Y
         )
      );
      assertEquals(
         surfaceY - 1,
         TellusCaveDepthMapper.actualYForVirtualFeature(
            TellusCaveDepthMapper.VANILLA_MAX_Y,
            TellusCaveDepthMapper.VANILLA_MAX_Y,
            surfaceY,
            bottomY
         )
      );
      assertEquals(
         bottomY,
         TellusCaveDepthMapper.actualYForVirtualFeature(
            TellusCaveDepthMapper.VANILLA_MIN_Y,
            TellusCaveDepthMapper.VANILLA_MAX_Y,
            surfaceY,
            bottomY
         )
      );
   }

   @Test
   void derivesVirtualSurfaceFromElevationAboveTellusSeaLevel() {
      assertEquals(
         TellusCaveDepthMapper.VANILLA_SEA_LEVEL,
         TellusCaveDepthMapper.virtualSurfaceForTellusColumn(500, 500)
      );
      assertEquals(163, TellusCaveDepthMapper.virtualSurfaceForTellusColumn(600, 500));
      assertEquals(
         TellusCaveDepthMapper.VANILLA_MAX_Y,
         TellusCaveDepthMapper.virtualSurfaceForTellusColumn(1_000, 500)
      );
   }

   @Test
   void locatesDeepslateRelativeToEachTerrainShell() {
      assertEquals(
         0,
         TellusCaveDepthMapper.actualDeepslateBoundaryY(
            TellusCaveDepthMapper.VANILLA_SEA_LEVEL,
            TellusCaveDepthMapper.VANILLA_MIN_Y,
            TellusCaveDepthMapper.VANILLA_SEA_LEVEL
         )
      );
      assertEquals(894, TellusCaveDepthMapper.actualDeepslateBoundaryY(1_000, 873, 0));
      assertEquals(-163, TellusCaveDepthMapper.actualDeepslateBoundaryY(-100, -227, 0));
   }

   @Test
   void rejectsFeaturesAboveTheirCorrespondingTerrainSurface() {
      assertEquals(
         Integer.MIN_VALUE,
         TellusCaveDepthMapper.actualYForVirtualFeature(200, 150, 900, 773)
      );
   }

   @Test
   void projectsTheVanillaProfileAcrossADeepTellusShell() {
      int surfaceY = 64;
      int bottomY = -191;

      assertEquals(
         TellusCaveDepthMapper.VANILLA_SEA_LEVEL,
         TellusCaveDepthMapper.virtualYForActualY(
            surfaceY,
            surfaceY,
            bottomY,
            TellusCaveDepthMapper.VANILLA_SEA_LEVEL,
            TellusCaveDepthMapper.VANILLA_MIN_Y
         )
      );
      assertEquals(
         TellusCaveDepthMapper.VANILLA_MIN_Y,
         TellusCaveDepthMapper.virtualYForActualY(
            bottomY,
            surfaceY,
            bottomY,
            TellusCaveDepthMapper.VANILLA_SEA_LEVEL,
            TellusCaveDepthMapper.VANILLA_MIN_Y
         )
      );
      assertEquals(
         -181,
         TellusCaveDepthMapper.actualYForVirtualFeature(
            -59,
            TellusCaveDepthMapper.VANILLA_SEA_LEVEL,
            surfaceY,
            bottomY
         )
      );
      assertTrue(
         TellusCaveDepthMapper.virtualYForActualY(
            -30,
            surfaceY,
            bottomY,
            TellusCaveDepthMapper.VANILLA_SEA_LEVEL,
            TellusCaveDepthMapper.VANILLA_MIN_Y
         ) > TellusCaveDepthMapper.VANILLA_MIN_Y
      );
   }
}
