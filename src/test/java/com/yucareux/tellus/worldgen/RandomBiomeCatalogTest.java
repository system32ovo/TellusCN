package com.yucareux.tellus.worldgen;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomBiomeCatalogTest {
   @Test
   void exposesEveryOverworldBiomeForEachSupportedVersion() {
      List<String> legacy = RandomBiomeCatalog.legacyOverworldBiomeIds();
      List<String> current = RandomBiomeCatalog.minecraft26_2OverworldBiomeIds();

      assertEquals(53, legacy.size());
      assertEquals(55, current.size());
      assertEquals(legacy.size(), new HashSet<>(legacy).size());
      assertEquals(current.size(), new HashSet<>(current).size());
      assertFalse(legacy.contains("pale_garden"));
      assertFalse(legacy.contains("sulfur_caves"));
      assertTrue(current.contains("pale_garden"));
      assertTrue(current.contains("sulfur_caves"));
      assertTrue(current.containsAll(legacy));
   }

   @Test
   void normalizesAndValidatesPersistedSelections() {
      assertEquals(
         List.of("desert", "sulfur_caves", "warm_ocean"),
         RandomBiomeCatalog.normalizeSelection(List.of("minecraft:DESERT", "sulfur_caves", "invalid", "warm_ocean", "desert"))
      );
      assertEquals(List.of(), RandomBiomeCatalog.normalizeSelection(List.of()));
      assertEquals(RandomBiomeCatalog.allKnownOverworldBiomeIds(), RandomBiomeCatalog.normalizeSelection(null));
      assertFalse(RandomBiomeCatalog.hasLandBiomeSelection(List.of("warm_ocean", "river", "sulfur_caves")));
      assertTrue(RandomBiomeCatalog.hasLandBiomeSelection(List.of("warm_ocean", "desert")));
   }

   @Test
   void removesBiomesThatDoNotExistInLegacyMinecraftVersions() {
      assertEquals(
         List.of("desert", "warm_ocean"),
         RandomBiomeCatalog.normalizeLegacySelection(List.of("desert", "pale_garden", "sulfur_caves", "warm_ocean"))
      );
      assertEquals(
         List.of("desert", "pale_garden", "sulfur_caves", "warm_ocean"),
         RandomBiomeCatalog.normalizeMinecraft26_2Selection(List.of("desert", "pale_garden", "sulfur_caves", "warm_ocean"))
      );
   }
}
