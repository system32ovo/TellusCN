package com.yucareux.tellus.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LoadingAttributionLayoutTest {
   private static final List<Integer> ENGLISH_LINE_GROUPS = List.of(3, 1, 2, 1);

   @Test
   void keepsTheWholeBlockBelowTheLoadingWidgetWhenItFits() {
      LoadingAttributionLayout.Layout layout = LoadingAttributionLayout.arrange(
            ENGLISH_LINE_GROUPS,
            9.0F,
            2.0F,
            20.0F,
            100.0F,
            140.0F,
            240.0F,
            true
         )
         .orElseThrow();

      assertEquals(List.of(new LoadingAttributionLayout.Block(0, 7, 140.0F)), layout.blocks());
   }

   @Test
   void splitsAtAnAttributionBoundaryWhenNeitherSideFitsTheWholeBlock() {
      LoadingAttributionLayout.Layout layout = LoadingAttributionLayout.arrange(
            ENGLISH_LINE_GROUPS,
            9.0F,
            2.0F,
            20.0F,
            54.0F,
            162.0F,
            223.0F,
            true
         )
         .orElseThrow();

      assertEquals(
         List.of(
            new LoadingAttributionLayout.Block(0, 3, 23.0F),
            new LoadingAttributionLayout.Block(3, 4, 162.0F)
         ),
         layout.blocks()
      );
   }

   @Test
   void canSplitWrappedLinesWhenNoWholeAttributionBoundaryFits() {
      assertTrue(
         LoadingAttributionLayout.arrange(
               ENGLISH_LINE_GROUPS,
               9.0F,
               2.0F,
               20.0F,
               40.0F,
               170.0F,
               223.0F,
               true
            )
            .isEmpty()
      );

      LoadingAttributionLayout.Layout layout = LoadingAttributionLayout.arrange(
            ENGLISH_LINE_GROUPS,
            9.0F,
            2.0F,
            20.0F,
            40.0F,
            170.0F,
            223.0F,
            false
         )
         .orElseThrow();

      assertEquals(2, layout.blocks().size());
      assertEquals(2, layout.blocks().get(0).lineCount());
      assertEquals(5, layout.blocks().get(1).lineCount());
   }

   @Test
   void returnsNoLayoutWhenTheSafeRegionsCannotHoldEveryLine() {
      assertTrue(
         LoadingAttributionLayout.arrange(
               ENGLISH_LINE_GROUPS,
               9.0F,
               2.0F,
               20.0F,
               30.0F,
               210.0F,
               220.0F,
               false
            )
            .isEmpty()
      );
   }
}
