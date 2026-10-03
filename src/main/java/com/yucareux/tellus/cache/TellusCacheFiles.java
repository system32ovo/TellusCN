package com.yucareux.tellus.cache;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class TellusCacheFiles {
   private TellusCacheFiles() {
   }

   public static boolean writeBytes(Path cachePath, byte[] data) throws IOException {
      return write(cachePath, output -> output.write(data));
   }

   public static boolean writeBytesIfCurrent(TellusCacheDomain domain, long generation, Path cachePath, byte[] data) throws IOException {
      return writeIfCurrent(domain, generation, cachePath, output -> output.write(data));
   }

   public static boolean writeStringIfCurrent(TellusCacheDomain domain, long generation, Path cachePath, String value, Charset charset) throws IOException {
      byte[] data = value.getBytes(charset);
      return writeBytesIfCurrent(domain, generation, cachePath, data);
   }

   public static boolean write(Path cachePath, TellusCacheFiles.OutputWriter writer) throws IOException {
      return writeIfCurrent(null, 0L, cachePath, writer);
   }

   public static Path createTempSibling(Path cachePath) throws IOException {
      Path parent = Objects.requireNonNull(cachePath.getParent(), "cachePathParent");
      Files.createDirectories(parent);
      return Files.createTempFile(parent, tempPrefix(cachePath), ".tmp");
   }

   public static boolean writeIfCurrent(
      TellusCacheDomain domain, long generation, Path cachePath, TellusCacheFiles.OutputWriter writer
   ) throws IOException {
      Objects.requireNonNull(cachePath, "cachePath");
      Objects.requireNonNull(writer, "writer");
      if (domain != null && !TellusCacheRegistry.isCurrent(domain, generation)) {
         return false;
      }

      Path tempPath = createTempSibling(cachePath);
      try {
         try (OutputStream output = Files.newOutputStream(tempPath)) {
            writer.write(output);
         }

         if (domain != null && !TellusCacheRegistry.isCurrent(domain, generation)) {
            return false;
         }

         moveIntoPlace(tempPath, cachePath);
         return true;
      } finally {
         Files.deleteIfExists(tempPath);
      }
   }

   public static void moveIntoPlace(Path source, Path target) throws IOException {
      try {
         Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
         Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   private static String tempPrefix(Path cachePath) {
      String name = cachePath.getFileName().toString();
      return name.length() >= 3 ? name + "-" : "cache-" + name + "-";
   }

   @FunctionalInterface
   public interface OutputWriter {
      void write(OutputStream output) throws IOException;
   }
}
