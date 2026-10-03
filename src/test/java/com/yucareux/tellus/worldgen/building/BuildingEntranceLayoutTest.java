package com.yucareux.tellus.worldgen.building;

import static org.junit.jupiter.api.Assertions.*;

import com.yucareux.tellus.world.data.osm.OsmBuildingFeature;
import com.yucareux.tellus.world.data.osm.OsmBuildingKind;
import com.yucareux.tellus.world.data.osm.OsmBuildingMetadata;
import com.yucareux.tellus.world.data.osm.RoadClass;
import com.yucareux.tellus.world.data.osm.RoadFeature;
import com.yucareux.tellus.world.data.osm.RoadMode;
import com.yucareux.tellus.worldgen.EarthProjection;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class BuildingEntranceLayoutTest {
   @Test
   void foundationSamplingUsesTheWholeFootprintAndExcludesCourtyardHeights() {
      var feature = feature(30, new double[]{-32,-32,32,-32,32,32,-32,32,-32,-32},
         new double[]{-16,-16,-16,16,16,16,16,-16,-16,-16});
      var sampled = new java.util.HashSet<String>();
      int height = BuildingPlacementSupport.sampleFoundationHeight(feature,List.of(),1,(x,z)->{
         assertTrue(feature.containsWorld(x+0.5,z+0.5,1));
         sampled.add(x+","+z);
         return x > 0 ? 65 : 63;
      });
      assertTrue(sampled.size() >= 8);
      assertEquals(65,height);
   }

   @Test
   void separateBuildingPartsShareTheirParentFoundation() {
      var parent = feature(40,new double[]{0,0,48,0,48,32,0,32,0,0});
      var left = feature(41,new double[]{0,0,16,0,16,32,0,32,0,0});
      double[] lons = java.util.stream.IntStream.range(0,left.pointCount(0)).mapToDouble(i -> left.lonAt(0,i)).toArray();
      double[] lats = java.util.stream.IntStream.range(0,left.pointCount(0)).mapToDouble(i -> left.latAt(0,i)).toArray();
      var part = new OsmBuildingFeature(OsmBuildingKind.PART,left.featureId(),parent.buildingId(),false,
         left.metadata(),left.heightMeters(),0,new double[][]{lons},new double[][]{lats});
      java.util.function.IntBinaryOperator slope = (x,z)->60+x/8;
      assertEquals(BuildingPlacementSupport.sampleFoundationHeight(parent,List.of(),1,slope),
         BuildingPlacementSupport.sampleFoundationHeight(part,List.of(parent),1,slope));
   }

   @Test
   void aLongStreetSegmentChoosesTheCorrectFrontWithoutANearbyMapVertex() {
      var road = road(-1000, -8, 1000, -8);
      var entry = BuildingEntranceLayout.choose(0, 19, 0, 19, (x,z) -> true, List.of(road), 1, (x,z) -> false);
      assertNotNull(entry);
      assertEquals(Direction.NORTH, entry.facing());
      assertEquals(0, entry.worldZ());
      assertEquals(entry, BuildingEntranceLayout.choose(0, 19, 0, 19, (x,z) -> true,
         List.of(road(1000, -8, -1000, -8)), 1, (x,z) -> false));
   }

   @Test
   void angledSteppedAndConcaveFootprintsHaveACompletePassageIntoTheMainInterior() {
      for (BiPredicate<Integer,Integer> shape : List.<BiPredicate<Integer,Integer>>of(
         (x,z) -> x + z >= 10 && x + z <= 34,
         (x,z) -> z >= x / 2 && z < x / 2 + 9,
         (x,z) -> x < 10 || z < 10,
         (x,z) -> x >= 8 && x <= 12 && z <= 8 || z >= 8)) {
         var entry = BuildingEntranceLayout.choose(0, 24, 0, 24, shape, List.of(road(-100, -8, 100, -8)), 1, (x,z) -> false);
         assertNotNull(entry);
         assertValid(entry, shape, 0, 24, 0, 24);
      }
   }

   @Test
   void courtyardsAndOneBlockSpursCannotBecomeTheOnlyEntrance() {
      BiPredicate<Integer,Integer> courtyard = (x,z) -> !(x >= 7 && x <= 17 && z >= 7 && z <= 17);
      var entry = BuildingEntranceLayout.choose(0, 24, 0, 24, courtyard, List.of(), 1, (x,z) -> false);
      assertNotNull(entry);
      assertTrue(entry.worldX() == 0 || entry.worldX() == 24 || entry.worldZ() == 0 || entry.worldZ() == 24);
      BiPredicate<Integer,Integer> spur = (x,z) -> x == 12 && z < 10 || z >= 10;
      entry = BuildingEntranceLayout.choose(0, 24, 0, 24, spur, List.of(road(-100,-8,100,-8)), 1, (x,z) -> false);
      assertNotNull(entry);
      assertTrue(entry.worldZ() >= 10);
      assertValid(entry, spur, 0,24,0,24);
   }

   @Test
   void neighboringBuildingsForceTheDoorOntoAnAccessibleSide() {
      var entry = BuildingEntranceLayout.choose(0, 19, 0, 19, (x,z) -> true,
         List.of(road(-100,-8,100,-8)), 1, (x,z) -> z < 0);
      assertNotNull(entry);
      assertNotEquals(Direction.NORTH, entry.facing());
      assertNull(BuildingEntranceLayout.choose(0,19,0,19,(x,z)->true,List.of(),1,(x,z)->true));
      assertNull(BuildingEntranceLayout.choose(0,1,0,15,(x,z)->true,List.of(),1,(x,z)->false));
   }

   @Test
   void factoryDoorsAgreeWithRasterizedCellsAtNegativeCoordinatesAndFractionalEdges() {
      var feature = feature(1, new double[]{-18.25,-15.75, 3.75,-7.5, 14.75,-26.25, -6.5,-36.25, -18.25,-15.75});
      var profile = BuildingFloorPlanTest.blueprint(24,24,3,Direction.NORTH,false).profile();
      var blueprint = TellusBuildingBlueprints.create("test",feature,profile,77,63,64,76,79,
         List.of(road(-100,-40,100,-40)),1);
      assertEquals(1, blueprint.entranceWidth());
      var plan = BuildingFloorPlan.create(blueprint,feature,1);
      assertEquals(0, plan.boundaryDistanceAt(blueprint.entranceWorldX(),blueprint.entranceWorldZ()));
      int deepest = -1;
      for(int step=1; step<=3; step++) {
         int x=blueprint.entranceWorldX()-step*blueprint.entranceFacing().getStepX();
         int z=blueprint.entranceWorldZ()-step*blueprint.entranceFacing().getStepZ();
         if(plan.boundaryDistanceAt(x,z)>0) {deepest=step; break;}
      }
      assertTrue(deepest>0);
      var reversed = feature(1,new double[]{-6.5,-36.25,14.75,-26.25,3.75,-7.5,-18.25,-15.75,-6.5,-36.25});
      var same=TellusBuildingBlueprints.create("test",reversed,profile,77,63,64,76,79,List.of(road(100,-40,-100,-40)),1);
      assertEquals(blueprint.entranceWorldX(),same.entranceWorldX());
      assertEquals(blueprint.entranceWorldZ(),same.entranceWorldZ());
      assertEquals(blueprint.entranceFacing(),same.entranceFacing());
   }

   private static void assertValid(BuildingEntranceLayout.Placement entry,BiPredicate<Integer,Integer> shape,int minX,int maxX,int minZ,int maxZ) {
      BiPredicate<Integer,Integer> occupied=(x,z)->x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ&&shape.test(x,z);
      boolean inside=false;
      for(int step=1;step<=3;step++) {
         int x=entry.worldX()-entry.facing().getStepX()*step,z=entry.worldZ()-entry.facing().getStepZ()*step;
         assertTrue(occupied.test(x,z));
         if(occupied.test(x-1,z)&&occupied.test(x+1,z)&&occupied.test(x,z-1)&&occupied.test(x,z+1)){inside=true;break;}
      }
      assertTrue(inside);
      for(int step=1;step<=6;step++) assertFalse(occupied.test(entry.worldX()+entry.facing().getStepX()*step,entry.worldZ()+entry.facing().getStepZ()*step));
   }

   static OsmBuildingFeature feature(long id,double[]... rings) {
      double[][] lons=new double[rings.length][],lats=new double[rings.length][];
      for(int ring=0;ring<rings.length;ring++) {
         lons[ring]=new double[rings[ring].length/2]; lats[ring]=new double[lons[ring].length];
         for(int i=0;i<lons[ring].length;i++) {
            lons[ring][i]=EarthProjection.blockXToLongitude(rings[ring][2*i],1);
            lats[ring][i]=EarthProjection.blockZToLat(rings[ring][2*i+1],1);
         }
      }
      return new OsmBuildingFeature(OsmBuildingKind.FOOTPRINT,id,"building-"+id,false,
         new OsmBuildingMetadata("house",null,null,null,3,null,null,null,null,null),12,0,lons,lats);
   }

   static RoadFeature road(double x1,double z1,double x2,double z2) {
      return new RoadFeature(1,RoadClass.NORMAL,RoadMode.NORMAL,0,"residential",
         new double[]{EarthProjection.blockXToLongitude(x1,1),EarthProjection.blockXToLongitude(x2,1)},
         new double[]{EarthProjection.blockZToLat(z1,1),EarthProjection.blockZToLat(z2,1)});
   }
}
