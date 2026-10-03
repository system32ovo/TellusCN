package com.yucareux.tellus.worldgen.building;

import com.yucareux.tellus.world.data.osm.OsmBuildingFeature;
import com.yucareux.tellus.world.data.osm.OsmBuildingKind;
import com.yucareux.tellus.world.data.osm.OsmBuildingMetadata;
import com.yucareux.tellus.worldgen.TellusBlockReferences;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TellusBuildingStylesTest {
   @Test
   void resolvesDistinctStylesForMainCategories() {
      assertEquals(
         BuildingStyle.FacadeFamily.BRICK_ROW,
         style(BuildingProfile.BuildingCategory.HOUSE, "house", "brick", "red", 2, 120.0, 9, 8).facadeFamily()
      );
      assertEquals(
         BuildingStyle.GroundFloorTreatment.LOBBY,
         style(BuildingProfile.BuildingCategory.RESIDENTIAL, "apartments", "brick", null, 6, 480.0, 12, 16).groundFloorTreatment()
      );
      assertEquals(
         BuildingStyle.GroundFloorTreatment.STOREFRONT,
         style(BuildingProfile.BuildingCategory.COMMERCIAL, "retail", "glass", null, 4, 700.0, 18, 18).groundFloorTreatment()
      );
      assertEquals(
         BuildingStyle.FacadeFamily.INDUSTRIAL,
         style(BuildingProfile.BuildingCategory.WAREHOUSE, "warehouse", "concrete", null, 2, 1200.0, 24, 18).facadeFamily()
      );
      assertEquals(
         BuildingStyle.WindowPattern.CURTAIN,
         style(BuildingProfile.BuildingCategory.GLASSY_SKYSCRAPER, "office", "glass", null, 18, 900.0, 16, 16).windowPattern()
      );
      assertNotEquals(
         BuildingStyle.FacadeFamily.INDUSTRIAL,
         style(BuildingProfile.BuildingCategory.GENERIC, null, null, null, 3, 300.0, 10, 10).facadeFamily()
      );
   }

   @Test
   void resolvesCategoriesFromBuildingMetadata() {
      assertCategory(BuildingProfile.BuildingCategory.HOUSE, feature(10L, metadata("house"), 6.0));
      assertCategory(BuildingProfile.BuildingCategory.RESIDENTIAL, feature(11L, metadata("apartments"), 18.0));
      assertCategory(BuildingProfile.BuildingCategory.COMMERCIAL, feature(12L, metadataWithShop("retail"), 12.0));
      assertCategory(BuildingProfile.BuildingCategory.OFFICE, feature(13L, metadataWithOffice("company"), 18.0));
      assertCategory(BuildingProfile.BuildingCategory.HOTEL, feature(14L, metadataWithTourism("hotel"), 16.0));
      assertCategory(BuildingProfile.BuildingCategory.INDUSTRIAL, feature(15L, metadata("industrial"), 9.0));
      assertCategory(BuildingProfile.BuildingCategory.WAREHOUSE, feature(16L, metadata("warehouse"), 9.0));
      assertCategory(BuildingProfile.BuildingCategory.SCHOOL, feature(17L, metadataWithAmenity("school"), 10.0));
      assertCategory(BuildingProfile.BuildingCategory.HOSPITAL, feature(18L, metadataWithAmenity("hospital"), 20.0));
      assertCategory(BuildingProfile.BuildingCategory.RELIGIOUS, feature(19L, metadataWithAmenity("place_of_worship"), 15.0));
      assertCategory(BuildingProfile.BuildingCategory.HISTORIC, feature(20L, metadataWithHistoric("castle"), 12.0));
      assertCategory(BuildingProfile.BuildingCategory.TOWER, feature(21L, metadataWithManMade("tower"), 35.0));
      assertCategory(BuildingProfile.BuildingCategory.GARAGE, feature(22L, metadata("garage"), 4.0));
      assertCategory(BuildingProfile.BuildingCategory.SHED, feature(23L, metadata("shed"), 4.0));
      assertCategory(BuildingProfile.BuildingCategory.GREENHOUSE, feature(24L, metadata("greenhouse"), 4.0));
      assertCategory(BuildingProfile.BuildingCategory.FARM, feature(25L, metadata("barn"), 7.0));
      assertCategory(BuildingProfile.BuildingCategory.TALL_BUILDING, feature(26L, metadata("office", "glass", null), 80.0));
      assertCategory(BuildingProfile.BuildingCategory.TALL_BUILDING, feature(1L, metadata("office", "concrete", null), 80.0));
      assertCategory(BuildingProfile.BuildingCategory.GLASSY_SKYSCRAPER, feature(26L, metadata("office", "glass", null), 180.0));
      assertCategory(BuildingProfile.BuildingCategory.MODERN_SKYSCRAPER, feature(1L, metadata("office", "concrete", null), 180.0));
   }

   @Test
   void mapsRoofShapeAndFacadeDetailsDeterministically() {
      assertEquals(BuildingProfile.RoofProfile.DOME, profileFor(feature(30L, metadata("church", null, null, "dome"), 18.0)).roofProfile());
      assertEquals(BuildingProfile.RoofProfile.SKILLION, profileFor(feature(31L, metadata("shed", null, null, "skillion"), 5.0)).roofProfile());
      assertEquals(BuildingProfile.RoofProfile.PYRAMIDAL, profileFor(feature(32L, metadata("tower", null, null, "pyramidal"), 25.0)).roofProfile());
      assertEquals(BuildingProfile.RoofProfile.FLAT_PARAPET, profileFor(feature(33L, metadata("office", null, null, "flat parapet", 2), 20.0)).roofProfile());
      BuildingProfile houseProfile = profileFor(feature(34L, metadata("house"), 8.0));
      assertTrue(houseProfile.roofProfile() == BuildingProfile.RoofProfile.GABLED_X || houseProfile.roofProfile() == BuildingProfile.RoofProfile.GABLED_Z);
      assertTrue(houseProfile.roofRise() >= 2);

      BuildingStyle garage = style(BuildingProfile.BuildingCategory.GARAGE, "garage", "concrete", null, 1, 90.0, 10, 8);
      assertTrue(garage.garageDoor());
      assertEquals(BuildingStyle.WallDepthStyle.INDUSTRIAL_BEAMS, garage.wallDepthStyle());

      BuildingStyle greenhouse = style(BuildingProfile.BuildingCategory.GREENHOUSE, "greenhouse", "glass", null, 1, 120.0, 10, 10);
      assertEquals(BuildingStyle.FacadeFamily.GREENHOUSE, greenhouse.facadeFamily());
      assertEquals(BuildingStyle.WallDepthStyle.GLASS_CURTAIN, greenhouse.wallDepthStyle());
      assertTrue(greenhouse.singleDoor());
   }

   @Test
   void materialHintsAndLodFacadeUseResolvedStyle() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
      BuildingProfile greenhouseProfile = profile(BuildingProfile.BuildingCategory.GREENHOUSE, 1);
      BuildingStyle greenhouseStyle = TellusBuildingStyles.resolveBuildingStyle(
         greenhouseProfile, metadata("greenhouse", "glass", null), 120.0, 10, 10, 99L
      );
      TellusBuildingMaterials.BuildingMaterialPalette greenhousePalette = TellusBuildingMaterials.resolvePalette(
         greenhouseProfile, greenhouseStyle, 99L
      );
      assertEquals(TellusBlockReferences.stainedGlassState("lime"), greenhousePalette.window());

      BuildingProfile towerProfile = profile(BuildingProfile.BuildingCategory.GLASSY_SKYSCRAPER, 20);
      BuildingStyle towerStyle = TellusBuildingStyles.resolveBuildingStyle(towerProfile, metadata("office", "glass", null), 900.0, 16, 16, 123L);
      BuildingBlueprint blueprint = new BuildingBlueprint("tower", 123L, towerProfile, towerStyle, 64, 65, 145, 148, 0, 15, 0, 15, 7, 0, Direction.NORTH, 1);
      TellusBuildingMaterials.BuildingMaterialPalette towerPalette = TellusBuildingMaterials.resolvePalette(towerProfile, towerStyle, 123L);
      int accentX = Math.floorMod(-towerStyle.accentPhase(), towerStyle.verticalAccentSpacing());
      assertEquals(towerPalette.window(), TellusBuildingMaterials.resolveLodFacadeBlock(blueprint, towerPalette, 0, accentX, 0, 5));
      assertEquals(towerPalette.trim(), TellusBuildingMaterials.resolveLodFacadeBlock(blueprint, towerPalette, 0, 0, 0, 5));
   }

   @Test
   void facadeDecisionsAreDeterministicAndExteriorDetailsFollowOsmScaleRange() {
      BuildingProfile profile = profile(BuildingProfile.BuildingCategory.RESIDENTIAL, 5);
      BuildingStyle style = TellusBuildingStyles.resolveBuildingStyle(
         profile, metadata("residential", "brick", null), 450.0, 12, 12, 12345L
      );
      BuildingBlueprint blueprint = new BuildingBlueprint("test", 12345L, profile, style, 64, 65, 85, 86, 0, 11, 0, 11, 5, 0, Direction.NORTH, 1);

      boolean first = TellusBuildingFacade.shouldPlaceBalcony(blueprint, 0, 2, 0, 2, blueprint.floorBottomY(2), blueprint.floorTopY(2));
      boolean second = TellusBuildingFacade.shouldPlaceBalcony(blueprint, 0, 2, 0, 2, blueprint.floorBottomY(2), blueprint.floorTopY(2));

      assertEquals(first, second);
      assertTrue(style.detailedExterior(1.0));
      assertTrue(style.detailedExterior(2.0));
      assertFalse(style.detailedExterior(16.0));
   }

   @Test
   void houseChimneysAreCappedAndOptional() {
      int zeroChimneyHouses = 0;
      int oneChimneyHouses = 0;
      int twoChimneyHouses = 0;

      for (long seed = 0L; seed < 200L; seed++) {
         BuildingProfile profile = pitchedProfile(BuildingProfile.BuildingCategory.HOUSE, BuildingProfile.RoofProfile.GABLED_X);
         BuildingStyle style = TellusBuildingStyles.resolveBuildingStyle(
            profile, metadata("house", "brick", null), 180.0, 14, 10, seed
         );
         BuildingBlueprint blueprint = new BuildingBlueprint(
            "house-" + seed, seed, profile, style, 64, 65, 73, 76, 0, 13, 0, 9, 6, 0, Direction.NORTH, 1
         );
         int chimneyCount = countChimneyCells(blueprint);

         assertTrue(chimneyCount <= 2, "expected at most two chimneys for seed " + seed);
         if (chimneyCount == 0) {
            zeroChimneyHouses++;
         } else if (chimneyCount == 1) {
            oneChimneyHouses++;
         } else {
            twoChimneyHouses++;
         }
      }

      assertTrue(zeroChimneyHouses > 0);
      assertTrue(oneChimneyHouses > 0);
      assertTrue(twoChimneyHouses > 0);
   }

   @Test
   void pitchedRoofsUseStairCompatibleMaterials() {
      for (long seed = 0L; seed < 200L; seed++) {
         BuildingProfile houseProfile = pitchedProfile(BuildingProfile.BuildingCategory.HOUSE, BuildingProfile.RoofProfile.GABLED_X);
         BuildingStyle houseStyle = TellusBuildingStyles.resolveBuildingStyle(houseProfile, metadata("house", "concrete", null), 180.0, 14, 10, seed);
         assertTrue(TellusBuildingMaterials.hasMatchingRoofStair(TellusBuildingMaterials.resolvePalette(houseProfile, houseStyle, seed).roof()));

         BuildingProfile residentialProfile = new BuildingProfile(
            BuildingProfile.Archetype.APARTMENT,
            BuildingProfile.BuildingCategory.RESIDENTIAL,
            BuildingProfile.RoofProfile.GABLED_Z,
            BuildingProfile.ClimateFamily.TEMPERATE,
            3,
            4,
            true,
            1,
            0,
            0,
            0,
            3
         );
         BuildingStyle residentialStyle = TellusBuildingStyles.resolveBuildingStyle(
            residentialProfile, metadata("apartments", "concrete", null), 360.0, 12, 14, seed
         );
         assertTrue(TellusBuildingMaterials.hasMatchingRoofStair(TellusBuildingMaterials.resolvePalette(residentialProfile, residentialStyle, seed).roof()));
      }
   }

   @Test
   void roofStairHelperMatchesResolvedRoofMaterial() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
      BuildingProfile houseProfile = pitchedProfile(BuildingProfile.BuildingCategory.HOUSE, BuildingProfile.RoofProfile.GABLED_X);
      BuildingStyle houseStyle = TellusBuildingStyles.resolveBuildingStyle(houseProfile, metadata("house", "brick", null), 180.0, 14, 10, 77L);
      TellusBuildingMaterials.BuildingMaterialPalette palette = TellusBuildingMaterials.resolvePalette(houseProfile, houseStyle, 77L);

      BlockState stair = TellusBuildingMaterials.resolveRoofStairBlock(palette, Direction.EAST);

      assertEquals(Blocks.BRICK_STAIRS, stair.getBlock());
      assertEquals(Direction.EAST, stair.getValue(BlockStateProperties.HORIZONTAL_FACING));
   }

   @Test
   void interiorRulesKeepFacadeRingOpen() {
      assertFalse(TellusBuildingFacade.shouldPlaceInteriorPartition(0));
      assertFalse(TellusBuildingFacade.shouldPlaceFurniture(1));
      assertTrue(TellusBuildingFacade.shouldPlaceInteriorPartition(2));
      assertTrue(TellusBuildingFacade.shouldPlaceFurniture(3));
   }

   private static void assertCategory(BuildingProfile.BuildingCategory expected, OsmBuildingFeature feature) {
      assertEquals(expected, profileFor(feature).category());
   }

   private static BuildingProfile profileFor(OsmBuildingFeature feature) {
      return TellusBuildingProfiles.resolveProfile(feature, 1.0, null, true);
   }

   private static BuildingStyle style(
      BuildingProfile.BuildingCategory category, String use, String material, String color, int floors, double area, int width, int depth
   ) {
      BuildingProfile profile = profile(category, floors);
      return TellusBuildingStyles.resolveBuildingStyle(profile, metadata(use, material, color), area, width, depth, 42L);
   }

   private static BuildingProfile profile(BuildingProfile.BuildingCategory category, int floors) {
      BuildingProfile.Archetype archetype = switch (category) {
         case HOUSE, FARM, GARAGE, SHED, GREENHOUSE -> BuildingProfile.Archetype.HOUSE;
         case RESIDENTIAL, HOTEL -> BuildingProfile.Archetype.APARTMENT;
         case COMMERCIAL, OFFICE, SCHOOL, HOSPITAL, RELIGIOUS, HISTORIC -> BuildingProfile.Archetype.COMMERCIAL;
         case INDUSTRIAL, WAREHOUSE -> BuildingProfile.Archetype.INDUSTRIAL;
         case TOWER, TALL_BUILDING, GLASSY_SKYSCRAPER, MODERN_SKYSCRAPER -> BuildingProfile.Archetype.TOWER;
         case GENERIC -> BuildingProfile.Archetype.GENERIC;
      };
      return new BuildingProfile(archetype, category, BuildingProfile.RoofProfile.FLAT, BuildingProfile.ClimateFamily.TEMPERATE, floors, 4, true, 1, 0, 0, 0, 4);
   }

   private static BuildingProfile pitchedProfile(BuildingProfile.BuildingCategory category, BuildingProfile.RoofProfile roofProfile) {
      return new BuildingProfile(
         BuildingProfile.Archetype.HOUSE,
         category,
         roofProfile,
         BuildingProfile.ClimateFamily.TEMPERATE,
         2,
         4,
         true,
         1,
         0,
         0,
         0,
         3
      );
   }

   private static int countChimneyCells(BuildingBlueprint blueprint) {
      int count = 0;
      for (int localX = 0; localX < blueprint.width(); localX++) {
         for (int localZ = 0; localZ < blueprint.depth(); localZ++) {
            int boundaryDistance = Math.min(Math.min(localX, blueprint.width() - 1 - localX), Math.min(localZ, blueprint.depth() - 1 - localZ));
            if (TellusBuildingFacade.shouldPlaceChimney(blueprint, boundaryDistance, blueprint.minWorldX() + localX, blueprint.minWorldZ() + localZ)) {
               count++;
            }
         }
      }

      return count;
   }

   private static OsmBuildingFeature feature(long id, OsmBuildingMetadata metadata, double heightMeters) {
      return new OsmBuildingFeature(
         OsmBuildingKind.FOOTPRINT,
         id,
         "building-" + id,
         false,
         metadata,
         heightMeters,
         0.0,
         new double[][]{{0.0, 0.00008, 0.00008, 0.0, 0.0}},
         new double[][]{{0.0, 0.0, 0.00008, 0.00008, 0.0}}
      );
   }

   private static OsmBuildingMetadata metadata(String use) {
      return metadata(use, null, null);
   }

   private static OsmBuildingMetadata metadata(String use, String material, String color) {
      return metadata(use, material, color, null);
   }

   private static OsmBuildingMetadata metadata(String use, String material, String color, String roofShape) {
      return metadata(use, material, color, roofShape, 0);
   }

   private static OsmBuildingMetadata metadata(String use, String material, String color, String roofShape, int roofLevels) {
      return new OsmBuildingMetadata(null, use, use, null, 1, roofShape, material, color, material, color, null, null, null, null, null, null, null, roofLevels, 0);
   }

   private static OsmBuildingMetadata metadataWithAmenity(String amenity) {
      return new OsmBuildingMetadata(null, null, null, null, 1, null, null, null, null, null, amenity, null, null, null, null, null, null, 0, 0);
   }

   private static OsmBuildingMetadata metadataWithTourism(String tourism) {
      return new OsmBuildingMetadata(null, null, null, null, 1, null, null, null, null, null, null, tourism, null, null, null, null, null, 0, 0);
   }

   private static OsmBuildingMetadata metadataWithOffice(String office) {
      return new OsmBuildingMetadata(null, null, null, null, 1, null, null, null, null, null, null, null, office, null, null, null, null, 0, 0);
   }

   private static OsmBuildingMetadata metadataWithShop(String shop) {
      return new OsmBuildingMetadata(null, null, null, null, 1, null, null, null, null, null, null, null, null, shop, null, null, null, 0, 0);
   }

   private static OsmBuildingMetadata metadataWithManMade(String manMade) {
      return new OsmBuildingMetadata(null, null, null, null, 1, null, null, null, null, null, null, null, null, null, manMade, null, null, 0, 0);
   }

   private static OsmBuildingMetadata metadataWithHistoric(String historic) {
      return new OsmBuildingMetadata(null, null, null, null, 1, null, null, null, null, null, null, null, null, null, null, historic, null, 0, 0);
   }
}
