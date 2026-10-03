package com.yucareux.tellus.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minecraft 1.21.1 background-rendering bridge for the shared preview screen. */
abstract class TerrainPreviewScreenVersionCompat extends Screen {
   protected TerrainPreviewScreenVersionCompat(Component title) {
      super(title);
   }

   protected final void renderVersionBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
      // Screen.render renders the background in this release.
   }
}
