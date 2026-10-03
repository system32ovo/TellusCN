package com.yucareux.tellus.mixin;

import com.yucareux.tellus.worldgen.HighYPackedCoordinateProfile;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockPos.class)
public abstract class BlockPosHighYMixin {
   @Mutable
   @Shadow
   @Final
   public static int PACKED_HORIZONTAL_LENGTH;

   @Mutable
   @Shadow
   @Final
   public static int PACKED_Y_LENGTH;

   @Mutable
   @Shadow
   @Final
   public static int MAX_HORIZONTAL_COORDINATE;

   @Inject(
      method = "<clinit>",
      at = @At("TAIL")
   )
   private static void tellus$installHighYProfile(CallbackInfo ci) {
      if (HighYPackedCoordinateProfile.isEnabled()) {
         PACKED_HORIZONTAL_LENGTH = HighYPackedCoordinateProfile.HORIZONTAL_BITS;
         PACKED_Y_LENGTH = HighYPackedCoordinateProfile.Y_BITS;
         MAX_HORIZONTAL_COORDINATE = HighYPackedCoordinateProfile.X_MAX;
      }
   }

   /**
    * @author Tellus
    * @reason Decodes X from the dense global Mercator coordinate profile.
    */
   @Overwrite
   public static int getX(long packed) {
      if (HighYPackedCoordinateProfile.isEnabled()) {
         return HighYPackedCoordinateProfile.unpackX(packed);
      }

      int leftShift = 64 - (PACKED_Y_LENGTH + PACKED_HORIZONTAL_LENGTH) - PACKED_HORIZONTAL_LENGTH;
      int rightShift = 64 - PACKED_HORIZONTAL_LENGTH;
      return (int)(packed << leftShift >> rightShift);
   }

   /**
    * @author Tellus
    * @reason Decodes Y from the dense global Mercator coordinate profile.
    */
   @Overwrite
   public static int getY(long packed) {
      if (HighYPackedCoordinateProfile.isEnabled()) {
         return HighYPackedCoordinateProfile.unpackY(packed);
      }

      return (int)(packed << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH);
   }

   /**
    * @author Tellus
    * @reason Decodes Z from the dense global Mercator coordinate profile.
    */
   @Overwrite
   public static int getZ(long packed) {
      if (HighYPackedCoordinateProfile.isEnabled()) {
         return HighYPackedCoordinateProfile.unpackZ(packed);
      }

      int zOffset = PACKED_Y_LENGTH;
      return unpackSigned(packed, zOffset, PACKED_HORIZONTAL_LENGTH);
   }

   /**
    * @author Tellus
    * @reason Encodes positions with the dense global Mercator coordinate profile.
    */
   @Overwrite
   public static long asLong(int x, int y, int z) {
      if (HighYPackedCoordinateProfile.isEnabled()) {
         return HighYPackedCoordinateProfile.packClamped(x, y, z);
      }

      long horizontalMask = (1L << PACKED_HORIZONTAL_LENGTH) - 1L;
      long yMask = (1L << PACKED_Y_LENGTH) - 1L;
      int zOffset = PACKED_Y_LENGTH;
      int xOffset = PACKED_Y_LENGTH + PACKED_HORIZONTAL_LENGTH;
      return ((long)x & horizontalMask) << xOffset | ((long)y & yMask) | ((long)z & horizontalMask) << zOffset;
   }

   private static int unpackSigned(long packed, int offset, int bits) {
      int leftShift = 64 - offset - bits;
      int rightShift = 64 - bits;
      return (int)(packed << leftShift >> rightShift);
   }
}
