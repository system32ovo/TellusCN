package com.yucareux.tellus.worldgen.building;

import com.yucareux.tellus.worldgen.arnis.ArnisBuildingRules;
import com.yucareux.tellus.worldgen.TellusBlockReferences;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

public final class TellusBuildingMaterials {
   private static final BlockState BUILDING_STATE = TellusBlockReferences.concreteState("GRAY");
   private static final BlockState BUILDING_WINDOW_STATE = TellusBlockReferences.stainedGlassState("LIGHT_GRAY");
   private static final BlockState BUILDING_TOWER_WINDOW_STATE = TellusBlockReferences.stainedGlassState("LIGHT_BLUE");
   private static final BlockState BUILDING_DARK_WINDOW_STATE = TellusBlockReferences.stainedGlassState("GRAY");
   private static final BlockState BUILDING_ROOF_STATE = TellusBlockReferences.concreteState("GRAY");
   private static final BlockState BUILDING_SLATE_ROOF_STATE = Blocks.DEEPSLATE_TILES.defaultBlockState();
   private static final BlockState BUILDING_CLAY_TILE_ROOF_STATE = Blocks.BRICKS.defaultBlockState();
   private static final BlockState BUILDING_STONE_ROOF_STATE = Blocks.STONE_BRICKS.defaultBlockState();
   private static final BlockState BUILDING_RESIDENTIAL_WALL_STATE = TellusBlockReferences.terracottaState("WHITE");
   private static final BlockState BUILDING_ARID_WALL_STATE = Blocks.SANDSTONE.defaultBlockState();
   private static final BlockState BUILDING_SANDSTONE_WALL_STATE = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
   private static final BlockState BUILDING_COLD_WALL_STATE = TellusBlockReferences.terracottaState("LIGHT_GRAY");
   private static final BlockState BUILDING_TROPICAL_WALL_STATE = Blocks.BIRCH_PLANKS.defaultBlockState();
   private static final BlockState BUILDING_BRICK_WALL_STATE = Blocks.BRICKS.defaultBlockState();
   private static final BlockState BUILDING_DARK_BRICK_WALL_STATE = TellusBlockReferences.terracottaState("BROWN");
   private static final BlockState BUILDING_RED_ACCENT_STATE = TellusBlockReferences.terracottaState("RED");
   private static final BlockState BUILDING_BLUE_ACCENT_STATE = TellusBlockReferences.terracottaState("BLUE");
   private static final BlockState BUILDING_METAL_STATE = Blocks.IRON_BARS.defaultBlockState();
   private static final BlockState BUILDING_AWNING_STATE = TellusBlockReferences.terracottaState("RED");
   private static final BlockState BUILDING_PLANTER_STATE = Blocks.MOSS_BLOCK.defaultBlockState();
   private static final BlockState BUILDING_PALE_STONE_WALL_STATE = Blocks.CALCITE.defaultBlockState();
   private static final BlockState BUILDING_COMMERCIAL_WALL_STATE = TellusBlockReferences.concreteState("LIGHT_GRAY");
   private static final BlockState BUILDING_INDUSTRIAL_WALL_STATE = Blocks.ANDESITE.defaultBlockState();
   private static final BlockState BUILDING_TOWER_WALL_STATE = TellusBlockReferences.terracottaState("CYAN");
   private static final BlockState BUILDING_TRIM_STATE = Blocks.POLISHED_ANDESITE.defaultBlockState();
   private static final BlockState BUILDING_WHITE_TRIM_STATE = Blocks.SMOOTH_QUARTZ.defaultBlockState();
   private static final BlockState BUILDING_SANDSTONE_TRIM_STATE = Blocks.CUT_SANDSTONE.defaultBlockState();
   private static final BlockState BUILDING_BRICK_TRIM_STATE = Blocks.STONE_BRICKS.defaultBlockState();
   private static final BlockState BUILDING_FLOOR_STATE = Blocks.POLISHED_ANDESITE.defaultBlockState();
   private static final BlockState BUILDING_RESIDENTIAL_FLOOR_STATE = Blocks.OAK_PLANKS.defaultBlockState();
   private static final BlockState BUILDING_PARTITION_STATE = Blocks.SMOOTH_STONE.defaultBlockState();
   private static final BlockState BUILDING_STAIR_STATE = Blocks.OAK_STAIRS.defaultBlockState();
   private static final BlockState BUILDING_COMMERCIAL_STAIR_STATE = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
   private static final BlockState BUILDING_SLAB_STATE = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
   private static final BlockState BUILDING_RESIDENTIAL_SLAB_STATE = Blocks.OAK_SLAB.defaultBlockState();
   private static final BlockState BUILDING_LIGHT_STATE = Blocks.SEA_LANTERN.defaultBlockState();
   private static final BlockState BUILDING_CLEAR_GLASS_STATE = Blocks.GLASS.defaultBlockState();
   private static final BlockState BUILDING_GREENHOUSE_GLASS_STATE = TellusBlockReferences.stainedGlassState("LIME");
   private static final BlockState BUILDING_WHITE_CONCRETE_STATE = TellusBlockReferences.concreteState("WHITE");
   private static final BlockState BUILDING_WOOD_WALL_STATE = Blocks.OAK_PLANKS.defaultBlockState();
   private static final BlockState BUILDING_DARK_WOOD_ROOF_STATE = Blocks.DARK_OAK_PLANKS.defaultBlockState();
   private static final BlockState BUILDING_METAL_WALL_STATE = Blocks.IRON_BLOCK.defaultBlockState();
   private static final BlockState BUILDING_DARK_ROOF_STATE = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
   private static final BlockState BUILDING_GREEN_ACCENT_STATE = TellusBlockReferences.terracottaState("GREEN");
   private static final BlockState[] RESIDENTIAL_WALL_VARIANTS = new BlockState[]{
      BUILDING_BRICK_WALL_STATE,
      Blocks.STONE_BRICKS.defaultBlockState(),
      BUILDING_RESIDENTIAL_WALL_STATE,
      BUILDING_SANDSTONE_WALL_STATE,
      Blocks.QUARTZ_BRICKS.defaultBlockState(),
      Blocks.MUD_BRICKS.defaultBlockState(),
      Blocks.POLISHED_GRANITE.defaultBlockState(),
      Blocks.END_STONE_BRICKS.defaultBlockState(),
      TellusBlockReferences.concreteState("BROWN"),
      Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
      TellusBlockReferences.concreteState("GRAY"),
      TellusBlockReferences.terracottaState("GRAY"),
      TellusBlockReferences.terracottaState("LIGHT_BLUE"),
      TellusBlockReferences.concreteState("LIGHT_GRAY"),
      TellusBlockReferences.terracottaState("LIGHT_GRAY"),
      Blocks.NETHER_BRICKS.defaultBlockState(),
      Blocks.POLISHED_ANDESITE.defaultBlockState(),
      Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
      Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
      Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
      Blocks.QUARTZ_BLOCK.defaultBlockState(),
      TellusBlockReferences.concreteState("WHITE"),
      TellusBlockReferences.terracottaState("ORANGE"),
      TellusBlockReferences.terracottaState("RED"),
      Blocks.RED_NETHER_BRICKS.defaultBlockState(),
      Blocks.GRANITE.defaultBlockState(),
      Blocks.TERRACOTTA.defaultBlockState(),
      BUILDING_COLD_WALL_STATE,
      BUILDING_PALE_STONE_WALL_STATE
   };
   private static final BlockState[] COMMERCIAL_WALL_VARIANTS = new BlockState[]{
      BUILDING_WHITE_CONCRETE_STATE,
      BUILDING_COMMERCIAL_WALL_STATE,
      TellusBlockReferences.concreteState("GRAY"),
      Blocks.POLISHED_ANDESITE.defaultBlockState(),
      Blocks.SMOOTH_STONE.defaultBlockState(),
      Blocks.QUARTZ_BLOCK.defaultBlockState(),
      Blocks.QUARTZ_BRICKS.defaultBlockState(),
      Blocks.STONE_BRICKS.defaultBlockState()
   };
   private static final BlockState[] INDUSTRIAL_WALL_VARIANTS = new BlockState[]{
      TellusBlockReferences.concreteState("GRAY"),
      TellusBlockReferences.concreteState("LIGHT_GRAY"),
      Blocks.STONE.defaultBlockState(),
      Blocks.SMOOTH_STONE.defaultBlockState(),
      Blocks.POLISHED_ANDESITE.defaultBlockState(),
      Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
      Blocks.BLACKSTONE.defaultBlockState()
   };
   private static final BlockState[] FARM_WALL_VARIANTS = new BlockState[]{
      Blocks.OAK_PLANKS.defaultBlockState(),
      Blocks.SPRUCE_PLANKS.defaultBlockState(),
      Blocks.DARK_OAK_PLANKS.defaultBlockState(),
      Blocks.COBBLESTONE.defaultBlockState(),
      Blocks.STONE.defaultBlockState(),
      Blocks.MUD_BRICKS.defaultBlockState(),
      Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
      TellusBlockReferences.terracottaState("BROWN")
   };
   private static final BlockState[] GARAGE_WALL_VARIANTS = new BlockState[]{
      BUILDING_BRICK_WALL_STATE,
      Blocks.STONE_BRICKS.defaultBlockState(),
      Blocks.POLISHED_ANDESITE.defaultBlockState(),
      Blocks.COBBLESTONE.defaultBlockState(),
      Blocks.SMOOTH_STONE.defaultBlockState(),
      TellusBlockReferences.concreteState("LIGHT_GRAY")
   };
   private static final BlockState[] INSTITUTIONAL_WALL_VARIANTS = new BlockState[]{
      BUILDING_WHITE_CONCRETE_STATE,
      BUILDING_COMMERCIAL_WALL_STATE,
      Blocks.QUARTZ_BRICKS.defaultBlockState(),
      Blocks.STONE_BRICKS.defaultBlockState(),
      Blocks.POLISHED_ANDESITE.defaultBlockState(),
      Blocks.SMOOTH_STONE.defaultBlockState(),
      Blocks.SANDSTONE.defaultBlockState(),
      Blocks.END_STONE_BRICKS.defaultBlockState()
   };
   private static final BlockState[] RELIGIOUS_WALL_VARIANTS = new BlockState[]{
      Blocks.STONE_BRICKS.defaultBlockState(),
      Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
      Blocks.QUARTZ_BLOCK.defaultBlockState(),
      TellusBlockReferences.concreteState("WHITE"),
      Blocks.SANDSTONE.defaultBlockState(),
      Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
      Blocks.POLISHED_DIORITE.defaultBlockState(),
      Blocks.END_STONE_BRICKS.defaultBlockState(),
      TellusBlockReferences.waxedOxidizedCopperState()
   };
   private static final BlockState[] HISTORIC_WALL_VARIANTS = new BlockState[]{
      Blocks.STONE_BRICKS.defaultBlockState(),
      Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
      Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
      Blocks.COBBLESTONE.defaultBlockState(),
      Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
      Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
      Blocks.POLISHED_ANDESITE.defaultBlockState(),
      Blocks.ANDESITE.defaultBlockState(),
      Blocks.SMOOTH_STONE.defaultBlockState(),
      BUILDING_BRICK_WALL_STATE,
      Blocks.RED_NETHER_BRICKS.defaultBlockState(),
      Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
      Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
      Blocks.COBBLED_DEEPSLATE.defaultBlockState()
   };
   private static final BlockState[] TOWER_WALL_VARIANTS = new BlockState[]{
      Blocks.STONE_BRICKS.defaultBlockState(),
      Blocks.COBBLESTONE.defaultBlockState(),
      Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
      BUILDING_BRICK_WALL_STATE,
      Blocks.POLISHED_ANDESITE.defaultBlockState(),
      Blocks.ANDESITE.defaultBlockState(),
      Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
      Blocks.SMOOTH_STONE.defaultBlockState()
   };
   private static final BlockState[] MODERN_SKYSCRAPER_WALL_VARIANTS = new BlockState[]{
      TellusBlockReferences.concreteState("GRAY"),
      TellusBlockReferences.concreteState("LIGHT_GRAY"),
      TellusBlockReferences.concreteState("WHITE"),
      Blocks.POLISHED_ANDESITE.defaultBlockState(),
      Blocks.SMOOTH_STONE.defaultBlockState(),
      Blocks.QUARTZ_BLOCK.defaultBlockState()
   };
   private static final BlockState[] GLASSY_SKYSCRAPER_WALL_VARIANTS = new BlockState[]{
      TellusBlockReferences.stainedGlassState("GRAY"),
      TellusBlockReferences.stainedGlassState("CYAN"),
      TellusBlockReferences.stainedGlassState("BLUE"),
      TellusBlockReferences.stainedGlassState("LIGHT_BLUE")
   };

   private TellusBuildingMaterials() {
   }

   public static TellusBuildingMaterials.BuildingMaterialPalette resolvePalette(BuildingBlueprint blueprint) {
      return resolvePalette(blueprint.profile(), blueprint.style(), blueprint.blueprintSeed());
   }

   public static TellusBuildingMaterials.BuildingMaterialPalette resolvePalette(BuildingProfile profile, long blueprintSeed) {
      return resolvePalette(profile, TellusBuildingStyles.resolveBuildingStyle(profile, null, 0.0, 0, 0, blueprintSeed), blueprintSeed);
   }

   public static TellusBuildingMaterials.BuildingMaterialPalette resolvePalette(BuildingProfile profile, BuildingStyle style, long blueprintSeed) {
      BuildingProfile.BuildingCategory category = profile.category();
      BlockState wall = switch (category) {
         case HOUSE, RESIDENTIAL, HOTEL -> switch (profile.climateFamily()) {
            case COLD -> BUILDING_COLD_WALL_STATE;
            case ARID -> BUILDING_ARID_WALL_STATE;
            case TROPICAL -> BUILDING_TROPICAL_WALL_STATE;
            case TEMPERATE -> BUILDING_RESIDENTIAL_WALL_STATE;
         };
         case FARM, SHED -> BUILDING_WOOD_WALL_STATE;
         case GARAGE -> BUILDING_COMMERCIAL_WALL_STATE;
         case COMMERCIAL, OFFICE, SCHOOL, HOSPITAL -> BUILDING_COMMERCIAL_WALL_STATE;
         case RELIGIOUS, HISTORIC -> BUILDING_STONE_ROOF_STATE;
         case INDUSTRIAL -> BUILDING_INDUSTRIAL_WALL_STATE;
         case WAREHOUSE -> BUILDING_TRIM_STATE;
         case GREENHOUSE -> BUILDING_GREENHOUSE_GLASS_STATE;
         case TOWER, TALL_BUILDING, GLASSY_SKYSCRAPER, MODERN_SKYSCRAPER -> BUILDING_TOWER_WALL_STATE;
         case GENERIC -> BUILDING_STATE;
      };
      if (category == BuildingProfile.BuildingCategory.HOUSE || category == BuildingProfile.BuildingCategory.RESIDENTIAL || category == BuildingProfile.BuildingCategory.HOTEL) {
         wall = selectState(blueprintSeed, 11, RESIDENTIAL_WALL_VARIANTS);
      } else if (category == BuildingProfile.BuildingCategory.COMMERCIAL || category == BuildingProfile.BuildingCategory.OFFICE) {
         wall = selectState(blueprintSeed, 17, COMMERCIAL_WALL_VARIANTS);
      } else if (category == BuildingProfile.BuildingCategory.SCHOOL || category == BuildingProfile.BuildingCategory.HOSPITAL) {
         wall = selectState(blueprintSeed, 23, INSTITUTIONAL_WALL_VARIANTS);
      } else if (category == BuildingProfile.BuildingCategory.INDUSTRIAL || category == BuildingProfile.BuildingCategory.WAREHOUSE) {
         wall = selectState(blueprintSeed, 29, INDUSTRIAL_WALL_VARIANTS);
      } else if (category == BuildingProfile.BuildingCategory.FARM) {
         wall = selectState(blueprintSeed, 30, FARM_WALL_VARIANTS);
      } else if (category == BuildingProfile.BuildingCategory.GARAGE) {
         wall = selectState(blueprintSeed, 28, GARAGE_WALL_VARIANTS);
      } else if (category == BuildingProfile.BuildingCategory.RELIGIOUS) {
         wall = selectState(blueprintSeed, 32, RELIGIOUS_WALL_VARIANTS);
      } else if (category == BuildingProfile.BuildingCategory.HISTORIC) {
         wall = selectState(blueprintSeed, 31, HISTORIC_WALL_VARIANTS);
      } else if (category == BuildingProfile.BuildingCategory.TOWER) {
         wall = selectState(blueprintSeed, 33, TOWER_WALL_VARIANTS);
      } else if (category == BuildingProfile.BuildingCategory.MODERN_SKYSCRAPER) {
         wall = selectState(blueprintSeed, 34, MODERN_SKYSCRAPER_WALL_VARIANTS);
      } else if (category == BuildingProfile.BuildingCategory.GLASSY_SKYSCRAPER) {
         wall = selectState(blueprintSeed, 35, GLASSY_SKYSCRAPER_WALL_VARIANTS);
      }
      BlockState secondaryWall = wall;
      BlockState trim = BUILDING_TRIM_STATE;
      BlockState accent = BUILDING_TRIM_STATE;
      BlockState roof = BUILDING_ROOF_STATE;
      if (category == BuildingProfile.BuildingCategory.HOUSE) {
         TellusBuildingStyles.HouseStyle houseStyle = TellusBuildingStyles.resolveHouseStyle(profile, blueprintSeed);
         wall = switch (houseStyle) {
            case WHITE_SLATE -> BUILDING_RESIDENTIAL_WALL_STATE;
            case WARM_CLAY -> BUILDING_SANDSTONE_WALL_STATE;
            case GRAY_CHARCOAL -> BUILDING_COLD_WALL_STATE;
            case BRICK_SLATE -> BUILDING_BRICK_WALL_STATE;
            case PALE_STONE -> BUILDING_PALE_STONE_WALL_STATE;
         };
         trim = switch (houseStyle) {
            case WHITE_SLATE -> BUILDING_WHITE_TRIM_STATE;
            case WARM_CLAY -> BUILDING_SANDSTONE_TRIM_STATE;
            case GRAY_CHARCOAL -> BUILDING_TRIM_STATE;
            case BRICK_SLATE, PALE_STONE -> BUILDING_BRICK_TRIM_STATE;
         };
         accent = trim;
         roof = switch (houseStyle) {
            case WHITE_SLATE, BRICK_SLATE -> BUILDING_SLATE_ROOF_STATE;
            case WARM_CLAY -> BUILDING_CLAY_TILE_ROOF_STATE;
            case GRAY_CHARCOAL -> BUILDING_ROOF_STATE;
            case PALE_STONE -> BUILDING_STONE_ROOF_STATE;
         };
      }

      switch (category) {
         case FARM, SHED -> {
            secondaryWall = BUILDING_TROPICAL_WALL_STATE;
            trim = BUILDING_DARK_WOOD_ROOF_STATE;
            accent = BUILDING_DARK_WOOD_ROOF_STATE;
            roof = BUILDING_DARK_WOOD_ROOF_STATE;
         }
         case GARAGE -> {
            secondaryWall = BUILDING_INDUSTRIAL_WALL_STATE;
            trim = BUILDING_TRIM_STATE;
            accent = BUILDING_METAL_WALL_STATE;
            roof = BUILDING_STONE_ROOF_STATE;
         }
         case GREENHOUSE -> {
            wall = BUILDING_GREENHOUSE_GLASS_STATE;
            secondaryWall = BUILDING_CLEAR_GLASS_STATE;
            trim = BUILDING_WHITE_TRIM_STATE;
            accent = BUILDING_GREEN_ACCENT_STATE;
            roof = BUILDING_CLEAR_GLASS_STATE;
         }
         case RELIGIOUS, HISTORIC -> {
            secondaryWall = BUILDING_PALE_STONE_WALL_STATE;
            trim = BUILDING_BRICK_TRIM_STATE;
            accent = BUILDING_WHITE_TRIM_STATE;
            roof = BUILDING_DARK_ROOF_STATE;
         }
         case SCHOOL, HOSPITAL -> {
            secondaryWall = BUILDING_WHITE_CONCRETE_STATE;
            trim = BUILDING_WHITE_TRIM_STATE;
            accent = category == BuildingProfile.BuildingCategory.HOSPITAL ? BUILDING_RED_ACCENT_STATE : BUILDING_BRICK_TRIM_STATE;
            roof = BUILDING_ROOF_STATE;
         }
         case OFFICE, COMMERCIAL, HOTEL -> {
            secondaryWall = BUILDING_WHITE_TRIM_STATE;
            trim = BUILDING_WHITE_TRIM_STATE;
            accent = BUILDING_BLUE_ACCENT_STATE;
            roof = BUILDING_ROOF_STATE;
         }
         case INDUSTRIAL, WAREHOUSE -> {
            secondaryWall = BUILDING_TRIM_STATE;
            trim = BUILDING_INDUSTRIAL_WALL_STATE;
            accent = BUILDING_METAL_WALL_STATE;
            roof = BUILDING_STONE_ROOF_STATE;
         }
         case TOWER, TALL_BUILDING, MODERN_SKYSCRAPER -> {
            secondaryWall = BUILDING_COMMERCIAL_WALL_STATE;
            trim = BUILDING_WHITE_TRIM_STATE;
            accent = BUILDING_BLUE_ACCENT_STATE;
            roof = BUILDING_DARK_ROOF_STATE;
         }
         case GLASSY_SKYSCRAPER -> {
            wall = BUILDING_TOWER_WINDOW_STATE;
            secondaryWall = BUILDING_CLEAR_GLASS_STATE;
            trim = BUILDING_WHITE_TRIM_STATE;
            accent = BUILDING_BLUE_ACCENT_STATE;
            roof = BUILDING_DARK_ROOF_STATE;
         }
         default -> {
         }
      }

      switch (style.facadeFamily()) {
         case BRICK_ROW -> {
            secondaryWall = category == BuildingProfile.BuildingCategory.HOUSE
                  || category == BuildingProfile.BuildingCategory.RESIDENTIAL
                  || category == BuildingProfile.BuildingCategory.HOTEL
               ? selectState(blueprintSeed, 12, RESIDENTIAL_WALL_VARIANTS)
               : profile.climateFamily() == BuildingProfile.ClimateFamily.ARID ? BUILDING_ARID_WALL_STATE : BUILDING_DARK_BRICK_WALL_STATE;
            accent = BUILDING_BRICK_TRIM_STATE;
            trim = BUILDING_BRICK_TRIM_STATE;
         }
         case MODERN_GRID -> {
            secondaryWall = BUILDING_WHITE_TRIM_STATE;
            accent = BUILDING_WHITE_TRIM_STATE;
            trim = BUILDING_TRIM_STATE;
         }
         case CURTAIN_WALL -> {
            wall = category == BuildingProfile.BuildingCategory.GLASSY_SKYSCRAPER ? BUILDING_TOWER_WINDOW_STATE : BUILDING_TOWER_WALL_STATE;
            secondaryWall = BUILDING_COMMERCIAL_WALL_STATE;
            accent = BUILDING_BLUE_ACCENT_STATE;
            trim = BUILDING_WHITE_TRIM_STATE;
         }
         case INDUSTRIAL -> {
            wall = BUILDING_INDUSTRIAL_WALL_STATE;
            secondaryWall = BUILDING_TRIM_STATE;
            accent = BUILDING_TRIM_STATE;
         }
         case STUCCO -> {
            secondaryWall = profile.climateFamily() == BuildingProfile.ClimateFamily.ARID ? BUILDING_SANDSTONE_WALL_STATE : BUILDING_PALE_STONE_WALL_STATE;
            accent = trim;
         }
         case MASONRY -> {
            secondaryWall = BUILDING_PALE_STONE_WALL_STATE;
            accent = trim;
         }
         case HISTORIC -> {
            secondaryWall = BUILDING_PALE_STONE_WALL_STATE;
            accent = BUILDING_BRICK_TRIM_STATE;
            trim = BUILDING_BRICK_TRIM_STATE;
         }
         case RELIGIOUS -> {
            secondaryWall = BUILDING_PALE_STONE_WALL_STATE;
            accent = BUILDING_WHITE_TRIM_STATE;
            trim = BUILDING_BRICK_TRIM_STATE;
         }
         case FARM -> {
            wall = BUILDING_WOOD_WALL_STATE;
            secondaryWall = BUILDING_TROPICAL_WALL_STATE;
            accent = BUILDING_DARK_WOOD_ROOF_STATE;
            trim = BUILDING_DARK_WOOD_ROOF_STATE;
         }
         case GREENHOUSE -> {
            wall = BUILDING_GREENHOUSE_GLASS_STATE;
            secondaryWall = BUILDING_CLEAR_GLASS_STATE;
            accent = BUILDING_GREEN_ACCENT_STATE;
            trim = BUILDING_WHITE_TRIM_STATE;
         }
      }

      if (category != BuildingProfile.BuildingCategory.GREENHOUSE) {
         wall = wallBlockForHint(style.wallMaterialHint(), wall);
      }
      if (category == BuildingProfile.BuildingCategory.HOUSE) {
         secondaryWall = wall;
      }
      roof = category == BuildingProfile.BuildingCategory.GREENHOUSE ? roof : roofBlockForHint(style.roofMaterialHint(), roof);
      if (isPitchedRoof(profile.roofProfile())) {
         roof = pitchedRoofBlock(roof, category, blueprintSeed);
      }

      BlockState floor = switch (profile.archetype()) {
         case HOUSE, APARTMENT -> category == BuildingProfile.BuildingCategory.GARAGE || category == BuildingProfile.BuildingCategory.GREENHOUSE
            ? BUILDING_FLOOR_STATE
            : BUILDING_RESIDENTIAL_FLOOR_STATE;
         default -> BUILDING_FLOOR_STATE;
      };
      BlockState stair = switch (profile.archetype()) {
         case HOUSE, APARTMENT -> BUILDING_STAIR_STATE;
         default -> BUILDING_COMMERCIAL_STAIR_STATE;
      };
      BlockState slab = switch (profile.archetype()) {
         case HOUSE, APARTMENT -> BUILDING_RESIDENTIAL_SLAB_STATE.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
         default -> BUILDING_SLAB_STATE.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
      };
      BlockState window = switch (category) {
         case GREENHOUSE -> BUILDING_GREENHOUSE_GLASS_STATE;
         case TOWER, TALL_BUILDING, GLASSY_SKYSCRAPER, MODERN_SKYSCRAPER, OFFICE, COMMERCIAL, HOTEL -> BUILDING_TOWER_WINDOW_STATE;
         default -> BUILDING_WINDOW_STATE;
      };
      BlockState awning = style.groundFloorTreatment() == BuildingStyle.GroundFloorTreatment.LOBBY ? BUILDING_WHITE_TRIM_STATE : BUILDING_AWNING_STATE;
      return new TellusBuildingMaterials.BuildingMaterialPalette(
         wall,
         secondaryWall,
         accent,
         trim,
         roof,
         window,
         BUILDING_DARK_WINDOW_STATE,
         floor,
         profile.archetype() == BuildingProfile.Archetype.HOUSE || profile.archetype() == BuildingProfile.Archetype.APARTMENT
            ? BUILDING_WHITE_CONCRETE_STATE : BUILDING_PARTITION_STATE,
         stair,
         slab,
         BUILDING_LIGHT_STATE,
         BUILDING_METAL_STATE,
         awning,
         BUILDING_PLANTER_STATE
      );
   }

   private static BlockState selectState(long seed, int salt, BlockState[] states) {
      if (states == null || states.length == 0) {
         return BUILDING_STATE;
      }

      long mixed = seed ^ (long)salt * -7046029254386353131L;
      mixed ^= mixed >>> 33;
      mixed *= -49064778989728563L;
      mixed ^= mixed >>> 33;
      return states[Math.floorMod((int)(mixed ^ mixed >>> 32), states.length)];
   }

   public static BlockState resolveLodFacadeBlock(
      BuildingBlueprint blueprint, TellusBuildingMaterials.BuildingMaterialPalette palette, int boundaryDistance, int floorIndex
   ) {
      return resolveLodFacadeBlock(blueprint, palette, boundaryDistance, blueprint.minWorldX(), blueprint.minWorldZ(), floorIndex);
   }

   public static BlockState resolveLodFacadeBlock(
      BuildingBlueprint blueprint,
      TellusBuildingMaterials.BuildingMaterialPalette palette,
      int boundaryDistance,
      int worldX,
      int worldZ,
      int floorIndex
   ) {
      int floorBottom = blueprint.floorBottomY(floorIndex);
      int floorTop = blueprint.floorTopY(floorIndex);
      int sampleY = Math.min(floorTop, floorBottom + Math.min(2, Math.max(1, blueprint.profile().storeyHeightBlocks() - 1)));
      return TellusBuildingFacade.resolveFacadeBlock(blueprint, palette, boundaryDistance, worldX, worldZ, floorIndex, floorBottom, floorTop, sampleY);
   }

   public static BlockState resolveLodRoofBlock(TellusBuildingMaterials.BuildingMaterialPalette palette, boolean roofEdge) {
      return roofEdge ? palette.trim() : palette.roof();
   }

   public static BlockState resolveLodRoofBlock(
      BuildingBlueprint blueprint, TellusBuildingMaterials.BuildingMaterialPalette palette, int boundaryDistance, int worldX, int worldZ
   ) {
      int highestFloor = blueprint.highestActiveFloor(boundaryDistance);
      if (blueprint.isFacadeCell(boundaryDistance, highestFloor)) {
         return palette.trim();
      }
      return palette.roof();
   }

   public static BlockState resolveLodRoofDetailBlock(
      BuildingBlueprint blueprint, TellusBuildingMaterials.BuildingMaterialPalette palette, int boundaryDistance, int worldX, int worldZ
   ) {
      if (TellusBuildingFacade.shouldPlaceChimney(blueprint, boundaryDistance, worldX, worldZ)) {
         return Blocks.BRICKS.defaultBlockState();
      }

      int highestFloor = blueprint.highestActiveFloor(boundaryDistance);
      if (blueprint.isFacadeCell(boundaryDistance, highestFloor)) {
         return null;
      }
      ArnisBuildingRules.RooftopEquipment equipment = ArnisBuildingRules.rooftopEquipmentAt(blueprint, boundaryDistance, worldX, worldZ);
      if (equipment != ArnisBuildingRules.RooftopEquipment.NONE) {
         return lodRoofEquipmentBlock(equipment, palette);
      }
      return null;
   }

   public static int resolveLodRoofDetailHeight(
      BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ
   ) {
      if (TellusBuildingFacade.shouldPlaceChimney(blueprint, boundaryDistance, worldX, worldZ)) {
         return 2;
      }

      ArnisBuildingRules.RooftopEquipment equipment = ArnisBuildingRules.rooftopEquipmentAt(blueprint, boundaryDistance, worldX, worldZ);
      return switch (equipment) {
         case WATER_TANK, ROOF_ACCESS -> 2;
         case HVAC, SOLAR_PANEL, ANTENNA, VENT_STACK -> 1;
         case NONE -> blueprint.style().roofDetail() == BuildingStyle.RoofDetail.ANTENNA ? 2 : 1;
      };
   }

   public static BlockState resolveRoofStairBlock(TellusBuildingMaterials.BuildingMaterialPalette palette, Direction facing) {
      BlockState roof = palette.roof();
      BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
      if (roof.getBlock() == Blocks.BRICKS) {
         stair = Blocks.BRICK_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.DEEPSLATE_TILES) {
         stair = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.DEEPSLATE_BRICKS) {
         stair = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.STONE_BRICKS) {
         stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.SANDSTONE || roof.getBlock() == Blocks.SMOOTH_SANDSTONE || roof.getBlock() == Blocks.CUT_SANDSTONE) {
         stair = Blocks.SANDSTONE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.DARK_OAK_PLANKS) {
         stair = Blocks.DARK_OAK_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.OAK_PLANKS) {
         stair = Blocks.OAK_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.BLACKSTONE) {
         stair = Blocks.BLACKSTONE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.POLISHED_BLACKSTONE_BRICKS) {
         stair = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.QUARTZ_BLOCK || roof.getBlock() == Blocks.SMOOTH_QUARTZ) {
         stair = Blocks.QUARTZ_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.COBBLESTONE) {
         stair = Blocks.COBBLESTONE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.MOSSY_COBBLESTONE) {
         stair = Blocks.MOSSY_COBBLESTONE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.MOSSY_STONE_BRICKS) {
         stair = Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.MUD_BRICKS) {
         stair = Blocks.MUD_BRICK_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.NETHER_BRICKS) {
         stair = Blocks.NETHER_BRICK_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.RED_NETHER_BRICKS) {
         stair = Blocks.RED_NETHER_BRICK_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.POLISHED_DEEPSLATE) {
         stair = Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.ANDESITE) {
         stair = Blocks.ANDESITE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.POLISHED_ANDESITE) {
         stair = Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.GRANITE) {
         stair = Blocks.GRANITE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.POLISHED_GRANITE) {
         stair = Blocks.POLISHED_GRANITE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.DIORITE) {
         stair = Blocks.DIORITE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.POLISHED_DIORITE) {
         stair = Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState();
      } else if (roof.getBlock() == Blocks.END_STONE_BRICKS) {
         stair = Blocks.END_STONE_BRICK_STAIRS.defaultBlockState();
      } else {
         return roof;
      }

      return stair.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? stair.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : stair;
   }

   private static BlockState lodRoofEquipmentBlock(ArnisBuildingRules.RooftopEquipment equipment, TellusBuildingMaterials.BuildingMaterialPalette palette) {
      return switch (equipment) {
         case HVAC -> Blocks.IRON_BLOCK.defaultBlockState();
         case SOLAR_PANEL -> Blocks.DAYLIGHT_DETECTOR.defaultBlockState();
         case ANTENNA, VENT_STACK -> palette.railing();
         case WATER_TANK -> Blocks.BARREL.defaultBlockState();
         case ROOF_ACCESS -> Blocks.STONE_BRICKS.defaultBlockState();
         case NONE -> palette.roof();
      };
   }

   private static BlockState wallBlockForHint(BuildingStyle.MaterialHint hint, BlockState fallback) {
      return switch (hint) {
         case BRICK, RED -> BUILDING_BRICK_WALL_STATE;
         case STONE -> BUILDING_STONE_ROOF_STATE;
         case SANDSTONE -> BUILDING_SANDSTONE_WALL_STATE;
         case WOOD, BROWN -> BUILDING_WOOD_WALL_STATE;
         case GLASS -> BUILDING_TOWER_WINDOW_STATE;
         case CONCRETE, GRAY -> BUILDING_COMMERCIAL_WALL_STATE;
         case DARK -> BUILDING_DARK_BRICK_WALL_STATE;
         case WHITE -> BUILDING_WHITE_CONCRETE_STATE;
         case BLUE -> BUILDING_BLUE_ACCENT_STATE;
         case GREEN -> BUILDING_GREEN_ACCENT_STATE;
         case METAL -> BUILDING_METAL_WALL_STATE;
         case NONE -> fallback;
      };
   }

   private static BlockState roofBlockForHint(BuildingStyle.MaterialHint hint, BlockState fallback) {
      return switch (hint) {
         case BRICK, RED -> BUILDING_CLAY_TILE_ROOF_STATE;
         case STONE, GRAY -> BUILDING_STONE_ROOF_STATE;
         case SANDSTONE -> BUILDING_SANDSTONE_TRIM_STATE;
         case WOOD, BROWN -> BUILDING_DARK_WOOD_ROOF_STATE;
         case GLASS -> BUILDING_CLEAR_GLASS_STATE;
         case CONCRETE, WHITE -> BUILDING_ROOF_STATE;
         case DARK, METAL -> BUILDING_DARK_ROOF_STATE;
         case BLUE -> BUILDING_BLUE_ACCENT_STATE;
         case GREEN -> BUILDING_GREEN_ACCENT_STATE;
         case NONE -> fallback;
      };
   }

   private static boolean isPitchedRoof(BuildingProfile.RoofProfile roofProfile) {
      return roofProfile == BuildingProfile.RoofProfile.GABLED_X
         || roofProfile == BuildingProfile.RoofProfile.GABLED_Z
         || roofProfile == BuildingProfile.RoofProfile.HIPPED
         || roofProfile == BuildingProfile.RoofProfile.PYRAMIDAL
         || roofProfile == BuildingProfile.RoofProfile.SKILLION;
   }

   private static BlockState pitchedRoofBlock(BlockState roof, BuildingProfile.BuildingCategory category, long blueprintSeed) {
      if (hasMatchingRoofStair(roof)) {
         return roof;
      }

      return switch (category) {
         case HOUSE -> selectState(blueprintSeed, 41, new BlockState[]{BUILDING_SLATE_ROOF_STATE, BUILDING_CLAY_TILE_ROOF_STATE, BUILDING_STONE_ROOF_STATE});
         case FARM, SHED -> BUILDING_DARK_WOOD_ROOF_STATE;
         case RELIGIOUS, HISTORIC, TOWER, TALL_BUILDING, GLASSY_SKYSCRAPER, MODERN_SKYSCRAPER -> BUILDING_DARK_ROOF_STATE;
         case GREENHOUSE -> BUILDING_CLEAR_GLASS_STATE;
         default -> BUILDING_STONE_ROOF_STATE;
      };
   }

   public static boolean hasMatchingRoofStair(BlockState roof) {
      return roof.getBlock() == Blocks.BRICKS
         || roof.getBlock() == Blocks.DEEPSLATE_TILES
         || roof.getBlock() == Blocks.DEEPSLATE_BRICKS
         || roof.getBlock() == Blocks.STONE_BRICKS
         || roof.getBlock() == Blocks.SANDSTONE
         || roof.getBlock() == Blocks.SMOOTH_SANDSTONE
         || roof.getBlock() == Blocks.CUT_SANDSTONE
         || roof.getBlock() == Blocks.DARK_OAK_PLANKS
         || roof.getBlock() == Blocks.OAK_PLANKS
         || roof.getBlock() == Blocks.BLACKSTONE
         || roof.getBlock() == Blocks.POLISHED_BLACKSTONE_BRICKS
         || roof.getBlock() == Blocks.QUARTZ_BLOCK
         || roof.getBlock() == Blocks.SMOOTH_QUARTZ
         || roof.getBlock() == Blocks.COBBLESTONE
         || roof.getBlock() == Blocks.MOSSY_COBBLESTONE
         || roof.getBlock() == Blocks.MOSSY_STONE_BRICKS
         || roof.getBlock() == Blocks.MUD_BRICKS
         || roof.getBlock() == Blocks.NETHER_BRICKS
         || roof.getBlock() == Blocks.RED_NETHER_BRICKS
         || roof.getBlock() == Blocks.POLISHED_DEEPSLATE
         || roof.getBlock() == Blocks.ANDESITE
         || roof.getBlock() == Blocks.POLISHED_ANDESITE
         || roof.getBlock() == Blocks.GRANITE
         || roof.getBlock() == Blocks.POLISHED_GRANITE
         || roof.getBlock() == Blocks.DIORITE
         || roof.getBlock() == Blocks.POLISHED_DIORITE
         || roof.getBlock() == Blocks.END_STONE_BRICKS;
   }

   public record BuildingMaterialPalette(
      BlockState wall,
      BlockState secondaryWall,
      BlockState accent,
      BlockState trim,
      BlockState roof,
      BlockState window,
      BlockState darkWindow,
      BlockState floor,
      BlockState partition,
      BlockState stair,
      BlockState slab,
      BlockState light,
      BlockState railing,
      BlockState awning,
      BlockState planter
   ) {
   }
}
