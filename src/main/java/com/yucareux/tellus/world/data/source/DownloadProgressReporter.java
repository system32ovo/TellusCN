package com.yucareux.tellus.world.data.source;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class DownloadProgressReporter {
   private static final ThreadLocal<DownloadProgressReporter.Listener> LISTENER = new ThreadLocal<>();

   private DownloadProgressReporter() {
   }

   
   public static DownloadProgressReporter.Scope push(DownloadProgressReporter.Listener listener) {
      Objects.requireNonNull(listener, "listener");
      DownloadProgressReporter.Listener previous = LISTENER.get();
      LISTENER.set(listener);
      return () -> {
         if (previous == null) {
            LISTENER.remove();
         } else {
            LISTENER.set(previous);
         }
      };
   }

   static DownloadProgressReporter.Listener currentListener() {
      return LISTENER.get();
   }

   public static void requestStarted(long expectedBytes) {
      DownloadProgressReporter.Listener listener = LISTENER.get();
      if (listener != null) {
         listener.onRequestStarted(expectedBytes);
      }
   }

   public static void expectedBytesKnown(long expectedBytes) {
      if (expectedBytes > 0L) {
         DownloadProgressReporter.Listener listener = LISTENER.get();
         if (listener != null) {
            listener.onExpectedBytesKnown(expectedBytes);
         }
      }
   }

   public static void bytesRead(int bytes) {
      if (bytes > 0) {
         DownloadProgressReporter.Listener listener = LISTENER.get();
         if (listener != null) {
            listener.onBytesRead(bytes);
         }
      }
   }

   public static void requestFinished() {
      DownloadProgressReporter.Listener listener = LISTENER.get();
      if (listener != null) {
         listener.onRequestFinished();
      }
   }

   public static byte[] readAllBytesWithProgress(InputStream input) throws IOException {
      return readAllBytesWithProgress(input, Integer.MAX_VALUE);
   }

   public static byte[] readAllBytesWithProgress(InputStream input, int maxBytes) throws IOException {
      Objects.requireNonNull(input, "input");
      if (maxBytes <= 0) {
         throw new IllegalArgumentException("Download byte limit must be positive");
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
      byte[] buffer = new byte[8192];

      int read;
      while ((read = input.read(buffer)) >= 0) {
         if (read != 0) {
            if (output.size() > maxBytes - read) {
               throw new IOException("Download exceeds the " + maxBytes + " byte safety limit");
            }
            output.write(buffer, 0, read);
            bytesRead(read);
         }
      }

      return output.toByteArray();
   }

   public interface Listener {
      default void onExpectedBytesKnown(long expectedBytes) {
      }

      void onRequestStarted(long var1);

      void onBytesRead(int var1);

      void onRequestFinished();
   }

   public interface Scope extends AutoCloseable {
      @Override
      void close();
   }
}
