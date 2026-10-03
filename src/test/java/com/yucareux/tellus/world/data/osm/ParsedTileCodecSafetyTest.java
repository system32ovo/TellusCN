package com.yucareux.tellus.world.data.osm;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParsedTileCodecSafetyTest {
   @TempDir
   Path tempDirectory;

   @Test
   void rejectsOversizedParsedCacheBeforeDecoding() throws IOException {
      Path cache = this.tempDirectory.resolve("oversized.tile");
      try (SeekableByteChannel channel = Files.newByteChannel(
         cache, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
      )) {
         channel.position(64L * 1024L * 1024L);
         channel.write(ByteBuffer.wrap(new byte[]{0}));
      }

      assertThrows(IOException.class, () -> ParsedTileCodec.readStreetLightTile(cache));
   }
}
