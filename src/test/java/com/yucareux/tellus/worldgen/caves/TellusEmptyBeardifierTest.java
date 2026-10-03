package com.yucareux.tellus.worldgen.caves;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.Test;

class TellusEmptyBeardifierTest {
   @Test
   void projectedVanillaCaveFieldIsIndependentOfRetargetedStructures() {
      DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(32, 2048, -48);

      assertEquals(0.0, TellusEmptyBeardifier.instance().compute(context));
   }
}
