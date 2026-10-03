package com.yucareux.tellus.client.widget;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
public class CustomizationList extends ContainerObjectSelectionList<CustomizationList.Entry> {
   private static final int ROW_HORIZONTAL_PADDING = 18;
   private static final int SCROLLBAR_RIGHT_PADDING = 10;
   private static final int SCROLLBAR_ROW_GAP = 8;
   private static final int ROW_VERTICAL_GAP = 4;
   private static final int WIDGET_HEIGHT = 20;

   public CustomizationList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
      super(minecraft, width, height, y, y + height, itemHeight + ROW_VERTICAL_GAP);
      this.centerListVertically = false;
      this.setRenderBackground(false);
      this.setRenderTopAndBottom(false);
   }

   public void clear() {
      this.clearEntries();
   }

   public void addWidget(AbstractWidget widget) {
      this.addEntry(new CustomizationList.Entry(widget));
   }

   public int getRowWidth() {
      int scrollbarX = this.getScrollbarPosition();
      int availableRight = scrollbarX - SCROLLBAR_ROW_GAP;
      int availableWidth = availableRight - (this.x0 + ROW_HORIZONTAL_PADDING);
      return Math.max(40, Math.min(this.width - ROW_HORIZONTAL_PADDING * 2, availableWidth));
   }

   public int getX() {
      return this.x0;
   }

   public void setX(int x) {
      this.x0 = x;
      this.x1 = x + this.width;
   }

   public void setY(int y) {
      this.y0 = y;
      this.y1 = y + this.height;
   }

   public void setWidth(int width) {
      this.width = width;
      this.x1 = this.x0 + width;
   }

   public void setHeight(int height) {
      this.height = height;
      this.y1 = this.y0 + height;
   }

   @Override
   protected int getScrollbarPosition() {
      return this.x0 + this.width - SCROLLBAR_RIGHT_PADDING;
   }

   protected void renderListBackground( GuiGraphics graphics) {
   }

   protected void renderListSeparators( GuiGraphics graphics) {
   }
   public static class Entry extends net.minecraft.client.gui.components.ContainerObjectSelectionList.Entry<CustomizationList.Entry> {
      private final AbstractWidget widget;

      public Entry(AbstractWidget widget) {
         this.widget = Objects.requireNonNull(widget, "widget");
      }

      
      public List<? extends GuiEventListener> children() {
         return Objects.requireNonNull(List.of(this.widget), "children");
      }

      
      public List<? extends NarratableEntry> narratables() {
         return Objects.requireNonNull(List.of(this.widget), "narratables");
      }

      public void render(
         GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta
      ) {
         this.widget.setX(left);
         this.widget.setY(top + Math.max(0, height - WIDGET_HEIGHT) / 2);
         this.widget.setWidth(width);
         WidgetCompat.setHeight(this.widget, Math.min(WIDGET_HEIGHT, height));
         this.widget.render(graphics, mouseX, mouseY, delta);
      }
   }
}
