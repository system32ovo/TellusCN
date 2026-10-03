package com.yucareux.tellus.client.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.yucareux.tellus.client.screen.MirrorSettingsScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;

/**
 * TellusCN 的 Mod Menu 集成入口。
 *
 * 作用：在 Mod Menu 的模组列表里给 TellusCN 挂一个"配置"按钮，
 * 点开后直接进入镜像设置界面，省去玩家自己找入口的麻烦。
 *
 * 说明：本类只编译期依赖 Mod Menu（modCompileOnly），运行期不打包 Mod Menu 本体，
 * 只有玩家自己装了 Mod Menu 时这个入口才会被调用。
 */
@Environment(EnvType.CLIENT)
public class TellusModMenuIntegration implements ModMenuApi {

   @Override
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      // 返回一个"给出父界面就构造出镜像设置界面"的工厂，Mod Menu 点配置时会调用它
      return (Screen parent) -> new MirrorSettingsScreen(parent);
   }
}
