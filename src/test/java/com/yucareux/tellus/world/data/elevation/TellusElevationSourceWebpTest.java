package com.yucareux.tellus.world.data.elevation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import org.junit.jupiter.api.Test;

class TellusElevationSourceWebpTest {
   private static final byte[] ZERO_ELEVATION_TILE = Base64.getDecoder().decode(
      "UklGRiwAAABXRUJQVlA4TCAAAAAv/8F/AAcQBYz+BwQCyf7eMxTR/4z//Oc///nPf/7zfw=="
   );

   @Test
   void decodesWebpWithoutRegisteredImageIoProvider() throws Exception {
      IIORegistry registry = IIORegistry.getDefaultInstance();
      WebPImageReaderSpi registeredProvider = registry.getServiceProviderByClass(WebPImageReaderSpi.class);
      if (registeredProvider != null) {
         registry.deregisterServiceProvider(registeredProvider);
      }

      try {
         assertFalse(ImageIO.getImageReadersByFormatName("webp").hasNext());

         ShortRaster raster = TellusElevationSource.readTerrainRaster(new ByteArrayInputStream(ZERO_ELEVATION_TILE));

         assertEquals(512, raster.width());
         assertEquals(512, raster.height());
         assertEquals(0, raster.get(0, 0));
         assertEquals(0, raster.get(511, 511));
      } finally {
         if (registeredProvider != null) {
            registry.registerServiceProvider(registeredProvider);
         }
      }
   }
}
