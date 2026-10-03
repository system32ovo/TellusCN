package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoadCrossingLayoutTest {
   private static final int SIDE = 16;
   private static final byte ROAD_CLASS = 1;
   private static final byte NORMAL_MODE = 1;
   private static final byte TUNNEL_MODE = 2;
   private static final int SCAN_RADIUS = 8;
   private static final int STRIPE_HALF_SPAN = 6;
   private static final int BAR_HALF_LENGTH = 3;

   @Test
   void centersAnchorInsteadOfUsingRoadEdge() {
      RoadGrid grid = new RoadGrid();
      grid.fillVerticalRoad(5, 11);

      RoadCrossingLayout.Anchor anchor = RoadCrossingLayout.findAnchor(
         5, 8, grid.roadClass, grid.roadMode, TUNNEL_MODE, SIDE, SCAN_RADIUS
      );

      assertNotNull(anchor);
      assertEquals(8, anchor.localX());
      assertEquals(8, anchor.localZ());
      assertFalse(anchor.roadHorizontal());
   }

   @Test
   void paintsVerticalBarsForVerticalRoad() {
      RoadGrid grid = new RoadGrid();
      grid.fillVerticalRoad(5, 11);
      RoadCrossingLayout.Anchor anchor = RoadCrossingLayout.findAnchor(
         8, 8, grid.roadClass, grid.roadMode, TUNNEL_MODE, SIDE, SCAN_RADIUS
      );

      Set<String> cells = encode(
         RoadCrossingLayout.markedCells(anchor, grid.roadClass, grid.roadMode, TUNNEL_MODE, SIDE, STRIPE_HALF_SPAN, BAR_HALF_LENGTH)
      );

      assertTrue(cells.contains(key(6, 5)));
      assertTrue(cells.contains(key(6, 11)));
      assertTrue(cells.contains(key(8, 5)));
      assertTrue(cells.contains(key(10, 11)));
      assertFalse(cells.contains(key(5, 8)));
      assertFalse(cells.contains(key(11, 8)));
      assertEquals(21, cells.size());
   }

   @Test
   void paintsHorizontalBarsForHorizontalRoad() {
      RoadGrid grid = new RoadGrid();
      grid.fillHorizontalRoad(5, 11);
      RoadCrossingLayout.Anchor anchor = RoadCrossingLayout.findAnchor(
         8, 8, grid.roadClass, grid.roadMode, TUNNEL_MODE, SIDE, SCAN_RADIUS
      );

      Set<String> cells = encode(
         RoadCrossingLayout.markedCells(anchor, grid.roadClass, grid.roadMode, TUNNEL_MODE, SIDE, STRIPE_HALF_SPAN, BAR_HALF_LENGTH)
      );

      assertTrue(anchor.roadHorizontal());
      assertTrue(cells.contains(key(5, 6)));
      assertTrue(cells.contains(key(11, 6)));
      assertTrue(cells.contains(key(5, 8)));
      assertTrue(cells.contains(key(11, 10)));
      assertFalse(cells.contains(key(8, 5)));
      assertFalse(cells.contains(key(8, 11)));
      assertEquals(21, cells.size());
   }

   private static Set<String> encode(List<RoadCrossingLayout.Cell> cells) {
      Set<String> encoded = new HashSet<>();
      for (RoadCrossingLayout.Cell cell : cells) {
         encoded.add(key(cell.localX(), cell.localZ()));
      }
      return encoded;
   }

   private static String key(int localX, int localZ) {
      return localX + "," + localZ;
   }

   private static final class RoadGrid {
      private final byte[] roadClass = new byte[SIDE * SIDE];
      private final byte[] roadMode = new byte[SIDE * SIDE];

      void fillVerticalRoad(int minX, int maxX) {
         for (int z = 0; z < SIDE; z++) {
            for (int x = minX; x <= maxX; x++) {
               this.setRoad(x, z);
            }
         }
      }

      void fillHorizontalRoad(int minZ, int maxZ) {
         for (int z = minZ; z <= maxZ; z++) {
            for (int x = 0; x < SIDE; x++) {
               this.setRoad(x, z);
            }
         }
      }

      private void setRoad(int localX, int localZ) {
         int index = localZ * SIDE + localX;
         this.roadClass[index] = ROAD_CLASS;
         this.roadMode[index] = NORMAL_MODE;
      }
   }
}
