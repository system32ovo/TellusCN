package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import org.junit.jupiter.api.Test;

class UndergroundStructureExclusionTest {
   private static final UndergroundStructureExclusion.Box ROOM =
      new UndergroundStructureExclusion.Box(100, 20, 200, 110, 30, 210);

   @Test
   void recognizesPiecesWithEnoughTerrainCover() {
      assertTrue(UndergroundStructureExclusion.isUnderground(92, 100));
      assertFalse(UndergroundStructureExclusion.isUnderground(93, 100));
   }

   @Test
   void separatesBeardBoxRoofProtectionFromFeaturePlacement() {
      assertTrue(UndergroundStructureExclusion.protectsProjectedCarving(TerrainAdjustment.BEARD_BOX));
      assertTrue(UndergroundStructureExclusion.protectsProjectedCarving(TerrainAdjustment.BURY));
      assertFalse(UndergroundStructureExclusion.protectsProjectedCarving(TerrainAdjustment.NONE));
      assertFalse(UndergroundStructureExclusion.protectsFeaturePlacement(TerrainAdjustment.BEARD_BOX));
      assertTrue(UndergroundStructureExclusion.protectsFeaturePlacement(TerrainAdjustment.BURY));
      assertFalse(UndergroundStructureExclusion.protectsFeaturePlacement(TerrainAdjustment.NONE));
   }

   @Test
   void protectsTheRoomAndACompactCarverBuffer() {
      List<UndergroundStructureExclusion.Box> boxes = List.of(ROOM);

      assertTrue(UndergroundStructureExclusion.blocksCarving(boxes, 100, 20, 200));
      assertTrue(UndergroundStructureExclusion.blocksCarving(boxes, 96, 16, 196));
      assertFalse(UndergroundStructureExclusion.blocksCarving(boxes, 95, 16, 196));
   }

   @Test
   void givesScansAndOffsetsALargerFeatureBuffer() {
      List<UndergroundStructureExclusion.Box> boxes = List.of(ROOM);

      assertTrue(UndergroundStructureExclusion.blocksFeaturePlacement(boxes, 84, 4, 184));
      assertFalse(UndergroundStructureExclusion.blocksFeaturePlacement(boxes, 83, 4, 184));
   }

   @Test
   void carvingOnlyCavernsStillAllowOresAndDeepDarkDecoration() {
      UndergroundStructureExclusion.Box cavern =
         new UndergroundStructureExclusion.Box(100, 20, 200, 110, 30, 210, false);
      List<UndergroundStructureExclusion.Box> boxes = List.of(cavern);

      assertTrue(UndergroundStructureExclusion.blocksCarving(boxes, 100, 20, 200));
      assertFalse(UndergroundStructureExclusion.blocksFeaturePlacement(boxes, 100, 20, 200));
   }

   @Test
   void supportsCompactPerPieceCarverBuffersForAncientCities() {
      UndergroundStructureExclusion.Box piece =
         new UndergroundStructureExclusion.Box(100, 20, 200, 110, 30, 210, 1, false);
      List<UndergroundStructureExclusion.Box> boxes = List.of(piece);

      assertTrue(UndergroundStructureExclusion.blocksCarving(boxes, 99, 19, 199));
      assertFalse(UndergroundStructureExclusion.blocksCarving(boxes, 98, 19, 199));
      assertFalse(UndergroundStructureExclusion.blocksFeaturePlacement(boxes, 100, 20, 200));
   }

   @Test
   void reportsHorizontalIntersectionWithMargin() {
      assertTrue(ROOM.intersectsHorizontal(80, 180, 90, 190, 10));
      assertFalse(ROOM.intersectsHorizontal(79, 179, 89, 189, 10));
   }

   @Test
   void rejectsInvertedBounds() {
      assertThrows(
         IllegalArgumentException.class,
         () -> new UndergroundStructureExclusion.Box(5, 0, 0, 4, 1, 1)
      );
   }

   @Test
   void rejectsNegativeCarvingMargins() {
      assertThrows(
         IllegalArgumentException.class,
         () -> new UndergroundStructureExclusion.Box(0, 0, 0, 1, 1, 1, -1, false)
      );
   }
}
