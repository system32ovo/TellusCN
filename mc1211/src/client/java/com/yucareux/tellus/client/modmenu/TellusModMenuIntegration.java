package com.yucareux.tellus.client.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.yucareux.tellus.client.screen.MirrorSettingsScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;

/**
 * TellusCN Mod Menu 集成
 *
 * 在 Mod Menu 中添加镜像设置入口
 */
@Environment(EnvType.CLIENT)
public class TellusModMenuIntegration implements ModMenuApi {

   @Override
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return (Screen parent) -> new MirrorSettingsScreen(parent);
   }
}
