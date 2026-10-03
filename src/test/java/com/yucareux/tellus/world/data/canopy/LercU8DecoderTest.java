package com.yucareux.tellus.world.data.canopy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class LercU8DecoderTest {
   private static final byte[] EMPTY_ARCGIS_TILE = Base64.getDecoder()
      .decode(
         "TGVyYzIgBQAAAEIx+msAAQAAAAEAAAEAAAAAAAEACAAAAEYAAAABAAAAAAAAAAAA4D8AAAAAAAAAAAAAAAAAAAAAAAAAAA=="
      );

   @Test
   void decodesOfficialEmptyLivingAtlasTile() throws Exception {
      LercU8Decoder.DecodedRaster raster = new LercU8Decoder().decode(EMPTY_ARCGIS_TILE);

      assertEquals(256, raster.width());
      assertEquals(256, raster.height());
      assertEquals(256 * 256, raster.pixels().length);
      for (int index = 0; index < raster.pixels().length; index++) {
         assertTrue(!raster.valid(index) || raster.pixels()[index] == 0);
      }
   }
}
