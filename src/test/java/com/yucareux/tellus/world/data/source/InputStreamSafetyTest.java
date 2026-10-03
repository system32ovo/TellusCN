package com.yucareux.tellus.world.data.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class InputStreamSafetyTest {
   @Test
   void acceptsTheExactLimitAndRejectsOneByteMore() throws IOException {
      byte[] data = new byte[]{1, 2, 3, 4};

      assertArrayEquals(data, InputStreamSafety.readAllBytes(new ByteArrayInputStream(data), 4, "test"));
      assertThrows(
         IOException.class,
         () -> InputStreamSafety.readAllBytes(new ByteArrayInputStream(data), 3, "test")
      );
   }
}
