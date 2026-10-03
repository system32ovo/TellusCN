package com.yucareux.tellus.preload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerrainPreloadStorageTest {
   @Test
   void rejectsIdentifiersThatCouldEscapeTheCacheRoot() {
      assertTrue(TerrainPreloadStorage.isValidIdentifier("preload-1234_test.v2"));
      assertFalse(TerrainPreloadStorage.isValidIdentifier("../../outside"));
      assertFalse(TerrainPreloadStorage.isValidIdentifier(".."));
      assertThrows(
         IllegalArgumentException.class,
         () -> TerrainPreloadStorage.instance().publishedDirectory("../../outside")
      );
   }
}
