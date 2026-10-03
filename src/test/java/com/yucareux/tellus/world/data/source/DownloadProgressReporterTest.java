package com.yucareux.tellus.world.data.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class DownloadProgressReporterTest {
   @Test
   void boundedReadAcceptsTheExactLimit() throws IOException {
      byte[] data = new byte[]{1, 2, 3, 4};

      assertArrayEquals(data, DownloadProgressReporter.readAllBytesWithProgress(new ByteArrayInputStream(data), data.length));
   }

   @Test
   void boundedReadRejectsAnOversizedResponse() {
      byte[] data = new byte[]{1, 2, 3, 4, 5};

      assertThrows(
         IOException.class,
         () -> DownloadProgressReporter.readAllBytesWithProgress(new ByteArrayInputStream(data), data.length - 1)
      );
   }
}
