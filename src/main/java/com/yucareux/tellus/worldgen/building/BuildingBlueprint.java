package com.yucareux.tellus.worldgen.building;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public record BuildingBlueprint(
   String groupId,
   long blueprintSeed,
   BuildingProfile profile,
   BuildingStyle style,
   int baseY,
   int floorY,
   int roofBaseY,
   int topY,
   int minWorldX,
   int maxWorldX,
   int minWorldZ,
   int maxWorldZ,
   int entranceWorldX,
   int entranceWorldZ,
   Direction entranceFacing,
   int entranceWidth
) {
   public BuildingBlueprint {
      entranceWidth = Math.max(0, entranceWidth);
      style = style == null ? TellusBuildingStyles.resolveBuildingStyle(profile, null, 0.0, maxWorldX - minWorldX + 1, maxWorldZ - minWorldZ + 1, blueprintSeed) : style;
   }

   public int width() {
      return this.maxWorldX - this.minWorldX + 1;
   }

   public int depth() {
      return this.maxWorldZ - this.minWorldZ + 1;
   }

   public int floorCount() {
      return this.profile.floorCount();
   }

   public boolean interiorsEnabled() {
      return this.profile.interiorsEnabled();
   }

   public int floorBottomY(int floorIndex) {
      int clamped = Mth.clamp(floorIndex, 0, this.floorCount() - 1);
      return this.floorY + clamped * this.profile.storeyHeightBlocks();
   }

   public int floorTopY(int floorIndex) {
      int clearHeight = Math.max(1, this.profile.storeyHeightBlocks() - 1);
      return Math.min(this.roofBaseY - 1, this.floorBottomY(floorIndex) + clearHeight);
   }

   public int setbackForFloor(int floorIndex) {
      int cadence = this.profile.setbackEveryFloors();
      // Keep enough space for a complete stairwell and usable rooms at the crown.
      int limit = Math.min(this.profile.maxSetback(), Math.max(0, (Math.min(this.width(), this.depth()) - 10) / 2));
      if (cadence <= 0 || limit <= 0) {
         return 0;
      }
      if (this.profile.archetype() == BuildingProfile.Archetype.TOWER && this.floorCount() >= 12) {
         int podiumFloors = 2 + Math.floorMod((int)this.blueprintSeed, 2);
         if (floorIndex < podiumFloors) {
            return 0;
         }
         int crownStart = Math.max(podiumFloors + 1, this.floorCount() * 3 / 4);
         int crownStep = Math.max(2, (this.floorCount() - crownStart) / Math.max(1, limit - 1));
         return Math.min(limit, 1 + Math.max(0, floorIndex - crownStart) / crownStep);
      }
      return Math.min(limit, floorIndex / cadence);
   }

   public boolean isEntranceCell(int worldX, int worldZ) {
      if (this.entranceWidth == 0) return false;
      return switch (this.entranceFacing) {
         case NORTH, SOUTH -> worldZ == this.entranceWorldZ && Math.abs(worldX - this.entranceWorldX) <= this.entranceWidth / 2;
         case EAST, WEST -> worldX == this.entranceWorldX && Math.abs(worldZ - this.entranceWorldZ) <= this.entranceWidth / 2;
         default -> false;
      };
   }

   public int entranceAlong(int x, int z) {
      return (x - this.entranceWorldX) * this.entranceFacing.getStepX() + (z - this.entranceWorldZ) * this.entranceFacing.getStepZ();
   }

   public int entranceAcross(int x, int z) {
      return Math.abs((x - this.entranceWorldX) * this.entranceFacing.getStepZ() - (z - this.entranceWorldZ) * this.entranceFacing.getStepX());
   }

   public boolean isEntrancePassage(int x, int z) {
      int along = this.entranceAlong(x, z);
      return this.entranceWidth > 0 && along <= 0 && along >= -BuildingEntranceLayout.PASSAGE_DEPTH
         && this.entranceAcross(x, z) <= this.entranceWidth / 2;
   }

   public boolean nearEntranceApproach(int x, int z) {
      int along = this.entranceAlong(x, z);
      return this.entranceWidth > 0 && along >= -BuildingEntranceLayout.PASSAGE_DEPTH - 2
         && along <= BuildingEntranceLayout.APPROACH_LENGTH + 2 && this.entranceAcross(x, z) <= 3;
   }

   public boolean isActiveOnFloor(int boundaryDistance, int floorIndex) {
      int clamped = Mth.clamp(floorIndex, 0, this.floorCount() - 1);
      return boundaryDistance >= this.setbackForFloor(clamped);
   }

   public boolean isFacadeCell(int boundaryDistance, int floorIndex) {
      int clamped = Mth.clamp(floorIndex, 0, this.floorCount() - 1);
      return this.isActiveOnFloor(boundaryDistance, clamped) && boundaryDistance == this.setbackForFloor(clamped);
   }

   public int floorIndexAtY(int worldY) {
      if (worldY <= this.floorY) {
         return 0;
      }

      return Mth.clamp((worldY - this.floorY) / this.profile.storeyHeightBlocks(), 0, this.floorCount() - 1);
   }

   public int highestActiveFloor(int boundaryDistance) {
      for (int floor = this.floorCount() - 1; floor >= 0; floor--) {
         if (boundaryDistance >= this.setbackForFloor(floor)) {
            return floor;
         }
      }

      return 0;
   }

   public int roofBaseY(int boundaryDistance) {
      return this.floorY + (this.highestActiveFloor(boundaryDistance) + 1) * this.profile.storeyHeightBlocks();
   }

   public int parapetHeight(int boundaryDistance) {
      return this.highestActiveFloor(boundaryDistance) < this.floorCount() - 1
         ? Math.min(1, this.profile.parapetHeight()) : this.profile.parapetHeight();
   }

   public int roofTopY(int worldX, int worldZ, int boundaryDistance) {
      int roofTop = this.roofBaseY(boundaryDistance);
      int width = this.width();
      int depth = this.depth();
      int localX = worldX - this.minWorldX;
      int localZ = worldZ - this.minWorldZ;
      return switch (this.profile.roofProfile()) {
         case FLAT -> roofTop;
         case FLAT_PARAPET -> roofTop;
         case FLAT_SKYLIGHT -> roofTop + (localX > 1 && localX < width - 2 && localZ > 1 && localZ < depth - 2 ? 1 : 0);
         case FLAT_CROWN -> roofTop + crownRise(boundaryDistance);
         case GABLED_X -> roofTop + gabledRise(depth, localZ);
         case GABLED_Z -> roofTop + gabledRise(width, localX);
         case HIPPED -> roofTop + Math.min(gabledRise(width, localX), gabledRise(depth, localZ));
         case PYRAMIDAL -> roofTop + Math.min(gabledRise(width, localX), gabledRise(depth, localZ));
         case SKILLION -> roofTop + (width >= depth ? skillionRise(width, localX) : skillionRise(depth, localZ));
         case DOME -> roofTop + domeRise(width, depth, localX, localZ);
      };
   }

   public int topYForFloor(int floorIndex, int worldX, int worldZ, int boundaryDistance) {
      if (floorIndex >= this.floorCount() - 1) {
         return this.roofTopY(worldX, worldZ, boundaryDistance);
      }

      return this.floorTopY(floorIndex);
   }

   private int crownRise(int boundaryDistance) {
      if (this.profile.roofRise() <= 0) {
         return 0;
      }

      int threshold = Math.max(1, this.profile.maxSetback() + 1);
      return boundaryDistance >= threshold ? this.profile.roofRise() : 0;
   }

   private int gabledRise(int span, int position) {
      if (this.profile.roofRise() <= 0 || span <= 2) {
         return 0;
      }

      double half = (span - 1) * 0.5;
      double distance = Math.abs(position - half);
      double normalized = 1.0 - distance / Math.max(1.0, half);
      return Math.max(0, (int)Math.round(this.profile.roofRise() * normalized));
   }

   private int skillionRise(int span, int position) {
      if (this.profile.roofRise() <= 0 || span <= 2) {
         return 0;
      }

      double normalized = Mth.clamp((double)position / Math.max(1, span - 1), 0.0, 1.0);
      return Math.max(0, (int)Math.round(this.profile.roofRise() * normalized));
   }

   private int domeRise(int width, int depth, int localX, int localZ) {
      if (this.profile.roofRise() <= 0 || width <= 2 || depth <= 2) {
         return 0;
      }

      double halfWidth = Math.max(1.0, (width - 1) * 0.5);
      double halfDepth = Math.max(1.0, (depth - 1) * 0.5);
      double dx = (localX - halfWidth) / halfWidth;
      double dz = (localZ - halfDepth) / halfDepth;
      double radius = Math.sqrt(dx * dx + dz * dz);
      double normalized = Math.max(0.0, 1.0 - radius);
      return Math.max(0, (int)Math.round(this.profile.roofRise() * normalized));
   }
}
