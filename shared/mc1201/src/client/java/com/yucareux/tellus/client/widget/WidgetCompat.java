package com.yucareux.tellus.client.widget;

import com.yucareux.tellus.mixin.client.AbstractWidgetAccessor;
import net.minecraft.client.gui.components.AbstractWidget;
public final class WidgetCompat {
   private WidgetCompat() {
   }

   public static void setHeight(AbstractWidget widget, int height) {
      ((AbstractWidgetAccessor)widget).tellus$setHeight(height);
   }
}
