package com.yucareux.tellus.world.data.source;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Bounded reads for network responses and cache files with untrusted lengths. */
public final class InputStreamSafety {
   private InputStreamSafety() {
   }

   public static byte[] readAllBytes(InputStream input, int maximumBytes, String label) throws IOException {
      Objects.requireNonNull(input, "input");
      Objects.requireNonNull(label, "label");
      if (maximumBytes <= 0) {
         throw new IllegalArgumentException("Input limit must be positive");
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(8192, maximumBytes));
      byte[] buffer = new byte[8192];
      int total = 0;
      int read;
      while ((read = input.read(buffer)) != -1) {
         if (read > maximumBytes - total) {
            throw new IOException(label + " exceeds the " + maximumBytes + " byte safety limit");
         }
         output.write(buffer, 0, read);
         total += read;
      }
      return output.toByteArray();
   }
}
