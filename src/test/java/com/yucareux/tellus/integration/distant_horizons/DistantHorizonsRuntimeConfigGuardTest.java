package com.yucareux.tellus.integration.distant_horizons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DistantHorizonsRuntimeConfigGuardTest {
   private static final String N_SIZED_OWNER = "com.seibel.distanthorizons.core.config.Config$Server$Experimental";
   private static final String N_SIZED_FIELD = "enableNSizedGeneration";
   private static final String UPSAMPLING_OWNER = "com.seibel.distanthorizons.core.config.Config$Common$LodBuilding$Experimental";
   private static final String UPSAMPLING_FIELD = "upsampleLowerDetailLodsToFillHoles";

   @Test
   void appliesOnceAndRestoresOnlyAfterLastDimensionReleases() {
      FakeResolver resolver = new FakeResolver();
      FakeConfigEntry nSized = resolver.add(N_SIZED_OWNER, N_SIZED_FIELD, false);
      FakeConfigEntry upsampling = resolver.add(UPSAMPLING_OWNER, UPSAMPLING_FIELD, true);
      DistantHorizonsRuntimeConfigGuard guard = new DistantHorizonsRuntimeConfigGuard(true, resolver);

      assertTrue(guard.acquire("minecraft:overworld"));
      assertTrue(nSized.value);
      assertEquals(1, nSized.writeCount);
      assertTrue(upsampling.value);
      assertEquals(0, upsampling.writeCount);

      assertTrue(guard.acquire("minecraft:the_nether"));
      assertFalse(guard.acquire("minecraft:overworld"));
      assertEquals(2, guard.activeDimensionCount());
      assertEquals(1, nSized.writeCount);
      assertEquals(0, upsampling.writeCount);

      guard.release("minecraft:overworld");
      assertTrue(nSized.value);
      assertTrue(upsampling.value);

      guard.release("minecraft:the_nether");
      assertFalse(nSized.value);
      assertTrue(upsampling.value);
      assertEquals(0, guard.activeDimensionCount());
      assertEquals(2, nSized.writeCount);
      assertEquals(0, upsampling.writeCount);
   }

   @Test
   void immediateReleaseRestoresValuesAfterRegistrationFailure() {
      FakeResolver resolver = new FakeResolver();
      FakeConfigEntry nSized = resolver.add(N_SIZED_OWNER, N_SIZED_FIELD, false);
      FakeConfigEntry upsampling = resolver.add(UPSAMPLING_OWNER, UPSAMPLING_FIELD, true);
      DistantHorizonsRuntimeConfigGuard guard = new DistantHorizonsRuntimeConfigGuard(true, resolver);

      boolean acquired = guard.acquire("minecraft:overworld");
      if (acquired) {
         guard.release("minecraft:overworld");
      }

      assertFalse(nSized.value);
      assertTrue(upsampling.value);
      assertEquals(0, guard.activeDimensionCount());
   }

   @Test
   void missingExperimentalFieldDoesNotBlockOtherOverride() {
      FakeResolver resolver = new FakeResolver();
      FakeConfigEntry nSized = resolver.add(N_SIZED_OWNER, N_SIZED_FIELD, false);
      DistantHorizonsRuntimeConfigGuard guard = new DistantHorizonsRuntimeConfigGuard(true, resolver);

      assertTrue(guard.acquire("minecraft:overworld"));
      assertTrue(nSized.value);
      guard.release("minecraft:overworld");
      assertFalse(nSized.value);
   }

   @Test
   void alreadyEnabledValueIsLeftUntouched() {
      FakeResolver resolver = new FakeResolver();
      FakeConfigEntry nSized = resolver.add(N_SIZED_OWNER, N_SIZED_FIELD, true);
      DistantHorizonsRuntimeConfigGuard guard = new DistantHorizonsRuntimeConfigGuard(true, resolver);

      assertTrue(guard.acquire("minecraft:overworld"));
      assertTrue(nSized.value);
      assertEquals(0, nSized.writeCount);

      guard.release("minecraft:overworld");
      assertTrue(nSized.value);
      assertEquals(0, nSized.writeCount);
      assertEquals(0, guard.activeDimensionCount());
   }

   @Test
   void upsamplingRemainsUserControlledWhenNSizedForcingIsDisabled() {
      FakeResolver resolver = new FakeResolver();
      FakeConfigEntry nSized = resolver.add(N_SIZED_OWNER, N_SIZED_FIELD, false);
      FakeConfigEntry upsampling = resolver.add(UPSAMPLING_OWNER, UPSAMPLING_FIELD, true);
      DistantHorizonsRuntimeConfigGuard guard = new DistantHorizonsRuntimeConfigGuard(false, resolver);

      guard.acquire("minecraft:overworld");
      assertFalse(nSized.value);
      assertTrue(upsampling.value);
      assertEquals(0, nSized.writeCount);
      assertEquals(0, upsampling.writeCount);
      guard.release("minecraft:overworld");
      assertTrue(upsampling.value);
   }

   private static final class FakeResolver implements DistantHorizonsRuntimeConfigGuard.ConfigEntryResolver {
      private final Map<String, FakeConfigEntry> entries = new HashMap<>();

      FakeConfigEntry add(String ownerClassName, String fieldName, boolean value) {
         FakeConfigEntry entry = new FakeConfigEntry(value);
         this.entries.put(key(ownerClassName, fieldName), entry);
         return entry;
      }

      @Override
      public DistantHorizonsRuntimeConfigGuard.BooleanConfigEntry resolve(String ownerClassName, String fieldName)
         throws NoSuchFieldException {
         FakeConfigEntry entry = this.entries.get(key(ownerClassName, fieldName));
         if (entry == null) {
            throw new NoSuchFieldException(fieldName);
         }
         return entry;
      }

      private static String key(String ownerClassName, String fieldName) {
         return ownerClassName + '#' + fieldName;
      }
   }

   private static final class FakeConfigEntry implements DistantHorizonsRuntimeConfigGuard.BooleanConfigEntry {
      private boolean value;
      private int writeCount;

      private FakeConfigEntry(boolean value) {
         this.value = value;
      }

      @Override
      public boolean get() {
         return this.value;
      }

      @Override
      public void setWithoutSaving(boolean value) {
         this.value = value;
         this.writeCount++;
      }
   }
}
