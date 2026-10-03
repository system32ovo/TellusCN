package com.yucareux.tellus.client.widget.map.component;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.client.widget.map.SlippyMap;
import com.yucareux.tellus.client.widget.map.SlippyMapPoint;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class MarkerMapComponent implements MapComponent {

   private static final Identifier WIDGETS_TEXTURE = Objects.requireNonNull(Tellus.id("textures/gui/widgets.png"), "widgetsTexture");
   private static final int TEXTURE_SIZE = 256;
   private static final int MARKER_SIZE = 16;
   private SlippyMapPoint marker;
   private boolean canMove;
   private float offsetX = 0.0F;
   private float offsetY = 32.0F;
   private boolean visible = true;

   public MarkerMapComponent(SlippyMapPoint marker) {
      this.marker = marker;
   }

   public MarkerMapComponent() {
      this(null);
   }

   public MarkerMapComponent allowMovement() {
      this.canMove = true;
      return this;
   }

   @Override
   public void onDrawMap(SlippyMap map, GuiGraphicsExtractor graphics, int mouseX, int mouseY, SlippyMapPoint mouse) {
      if (this.marker != null && this.visible) {
         int scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
         int markerX = this.marker.getX(map.getCameraZoom()) - map.getCameraX();
         int markerY = this.marker.getY(map.getCameraZoom()) - map.getCameraY();
         int guiMarkerX = markerX / scale;
         int guiMarkerY = markerY / scale;
         graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            WIDGETS_TEXTURE,
            guiMarkerX - MARKER_SIZE / 2,
            guiMarkerY - MARKER_SIZE,
            this.offsetX,
            this.offsetY,
            MARKER_SIZE,
            MARKER_SIZE,
            TEXTURE_SIZE,
            TEXTURE_SIZE
         );
      }
   }

   @Override
   public boolean onMouseReleased(SlippyMap map, SlippyMapPoint mouse, int button) {
      if (this.canMove) {
         this.marker = mouse;
         return true;
      } else {
         return false;
      }
   }

   public void moveMarker(double latitude, double longitude) {
      this.marker = new SlippyMapPoint(latitude, longitude);
   }

   public SlippyMapPoint getMarker() {
      return this.marker;
   }

   public void setOffsetX(float x) {
      this.offsetX = x;
   }

   public void setOffsetY(float y) {
      this.offsetY = y;
   }

   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   public boolean isVisible() {
      return this.visible;
   }
}
