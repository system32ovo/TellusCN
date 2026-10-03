package com.yucareux.tellus.client.screen;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.config.MirrorConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import java.util.Objects;

/**
 * TellusCN 镜像设置界面（1.20.1 ~ 1.21.1 通用版本）
 *
 * 为中国玩家提供便捷的数据源镜像配置。
 *
 * 实现说明：这里刻意只使用 Button 与 EditBox，不使用 Checkbox / CycleButton，
 * 因为这两个控件的构造方式在 1.20.1 与 1.21.1 之间发生了变化（旧构造器 vs builder），
 * 只用 Button 才能让同一份代码同时编译通过两个版本。
 */
@Environment(EnvType.CLIENT)
public class MirrorSettingsScreen extends Screen {

   private static final Component TITLE = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.title").withStyle(ChatFormatting.BOLD),
      "title"
   );

   private static final Component DESCRIPTION = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.description"),
      "description"
   );

   private static final Component ENABLE_MIRROR = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.enable"),
      "enableMirror"
   );

   private static final Component DISABLE_MIRROR = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.disable_mirror"),
      "disableMirror"
   );

   private static final Component USE_OFFICIAL = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.use_official"),
      "useOfficial"
   );

   private static final Component USE_CUSTOM = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.use_custom"),
      "useCustom"
   );

   private static final Component CUSTOM_DOMAIN_LABEL = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.custom_domain"),
      "customDomainLabel"
   );

   private static final Component CUSTOM_DOMAIN_HINT = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.custom_domain_hint"),
      "customDomainHint"
   );

   private static final Component CURRENT_STATUS = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.current_status"),
      "currentStatus"
   );

   private static final Component STATUS_ENABLED = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.status_enabled").withStyle(ChatFormatting.GREEN),
      "statusEnabled"
   );

   private static final Component STATUS_DISABLED = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.status_disabled").withStyle(ChatFormatting.RED),
      "statusDisabled"
   );

   private static final Component SAVE_BUTTON = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.save"),
      "saveButton"
   );

   private static final Component BACK_BUTTON = Objects.requireNonNull(
      Component.translatable("gui.back"),
      "backButton"
   );

   // 返回时要回到的上一个界面
   private final Screen parent;

   // UI 组件：用切换按钮代替复选框，保证 1.20.1 与 1.21.1 都有相同的构造方式
   private Button enableButton;
   private Button officialModeButton;
   private Button customModeButton;
   private EditBox customDomainEditBox;
   private Button saveButton;

   // 临时状态：只有点"保存"时才会写回 MirrorConfig，点返回则丢弃
   private boolean tempEnabled;
   private MirrorMode tempMode;
   private String tempCustomDomain;
   private int tempPresetIndex;

   private enum MirrorMode {
      OFFICIAL,   // 使用官方预设
      CUSTOM      // 使用自定义域名
   }

   public MirrorSettingsScreen(Screen parent) {
      super(TITLE);
      this.parent = parent;

      // 加载当前配置到临时状态
      this.tempEnabled = MirrorConfig.isEnabled();
      this.tempCustomDomain = MirrorConfig.getCustomDomain();
      this.tempPresetIndex = MirrorConfig.getSelectedPreset();

      // 判断当前模式：填了自定义域名就算自定义模式，否则算官方预设模式
      if (!this.tempCustomDomain.isBlank()) {
         this.tempMode = MirrorMode.CUSTOM;
      } else {
         this.tempMode = MirrorMode.OFFICIAL;
      }
   }

   @Override
   protected void init() {
      // 界面横向中心，所有控件以它为基准左右摆放
      int centerX = this.width / 2;
      // 第一行控件的纵向起始位置
      int startY = 60;
      // 每一行之间的纵向间距
      int lineHeight = 25;

      // 启用/关闭镜像的切换按钮：点击后立刻翻转临时状态并刷新界面
      this.enableButton = Button.builder(
            this.tempEnabled ? DISABLE_MIRROR : ENABLE_MIRROR,
            button -> {
               this.tempEnabled = !this.tempEnabled;
               button.setMessage(this.tempEnabled ? DISABLE_MIRROR : ENABLE_MIRROR);
               this.updateUIState();
            })
         .bounds(centerX - 150, startY, 300, 20)
         .build();
      this.addRenderableWidget(this.enableButton);

      // 官方预设模式按钮
      this.officialModeButton = Button.builder(
            USE_OFFICIAL,
            button -> {
               this.tempMode = MirrorMode.OFFICIAL;
               this.updateUIState();
            })
         .bounds(centerX - 150, startY + lineHeight * 2, 145, 20)
         .build();
      this.addRenderableWidget(this.officialModeButton);

      // 自定义模式按钮
      this.customModeButton = Button.builder(
            USE_CUSTOM,
            button -> {
               this.tempMode = MirrorMode.CUSTOM;
               this.updateUIState();
            })
         .bounds(centerX + 5, startY + lineHeight * 2, 145, 20)
         .build();
      this.addRenderableWidget(this.customModeButton);

      // 自定义域名输入框
      this.customDomainEditBox = new EditBox(
         this.font,
         centerX - 150, startY + lineHeight * 4, 300, 20,
         CUSTOM_DOMAIN_LABEL
      );
      this.customDomainEditBox.setValue(this.tempCustomDomain);
      this.customDomainEditBox.setHint(CUSTOM_DOMAIN_HINT);
      // 喵~防御：限制长度，避免用户粘贴超长文本撑坏界面或写入异常配置
      this.customDomainEditBox.setMaxLength(200);
      this.addRenderableWidget(this.customDomainEditBox);

      // 保存按钮
      this.saveButton = Button.builder(SAVE_BUTTON, button -> this.saveAndClose())
         .bounds(centerX - 155, this.height - 30, 150, 20)
         .build();
      this.addRenderableWidget(this.saveButton);

      // 取消/返回按钮
      this.addRenderableWidget(Button.builder(BACK_BUTTON, button -> this.onClose())
         .bounds(centerX + 5, this.height - 30, 150, 20)
         .build());

      // 首次布局完成后按当前状态刷新一次按钮可用性
      this.updateUIState();
   }

   private void updateUIState() {
      // 更新模式按钮状态：当前选中的那个按钮置灰，避免重复点击
      this.officialModeButton.active = this.tempEnabled && this.tempMode != MirrorMode.OFFICIAL;
      this.customModeButton.active = this.tempEnabled && this.tempMode != MirrorMode.CUSTOM;

      // 只有"启用镜像 + 自定义模式"时才允许编辑域名
      this.customDomainEditBox.active = this.tempEnabled && this.tempMode == MirrorMode.CUSTOM;

      // 更新启用按钮文案，让用户一眼看出点下去会发生什么
      if (this.tempEnabled) {
         this.enableButton.setMessage(DISABLE_MIRROR);
      } else {
         this.enableButton.setMessage(ENABLE_MIRROR);
      }
   }

   private void saveAndClose() {
      // 保存启用状态
      MirrorConfig.setEnabled(this.tempEnabled);

      if (this.tempEnabled) {
         if (this.tempMode == MirrorMode.CUSTOM) {
            // 自定义模式：保存用户填写的域名（去掉首尾空白）
            String domain = this.customDomainEditBox.getValue().trim();
            // 喵~防御：空白域名等同于没配置，直接回落成空串，让后续逻辑走官方源
            MirrorConfig.setCustomDomain(domain);
         } else {
            // 使用官方预设，清空自定义域名
            MirrorConfig.setCustomDomain("");
            MirrorConfig.setSelectedPreset(this.tempPresetIndex);
         }
      }

      Tellus.LOGGER.info("Mirror config saved: enabled={}, mode={}, domain={}",
         this.tempEnabled, this.tempMode, this.customDomainEditBox.getValue());

      this.onClose();
   }

   @Override
   public void onClose() {
      if (this.minecraft != null) {
         // 1.20.1 ~ 1.21.1 都支持在 Minecraft 实例上直接切换界面
         this.minecraft.setScreen(this.parent);
      }
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      // 背景交给 super.render 处理：不同小版本对 renderBackground 的签名不同，避免写死版本 API
      super.render(graphics, mouseX, mouseY, partialTick);

      // 界面横向中心
      int centerX = this.width / 2;

      // 绘制标题
      graphics.drawCenteredString(this.font, this.title, centerX, 20, 0xFFFFFF);

      // 绘制说明
      graphics.drawCenteredString(this.font, DESCRIPTION, centerX, 40, 0xAAAAAA);

      // 绘制当前状态
      Component status = MirrorConfig.isEnabled() ? STATUS_ENABLED : STATUS_DISABLED;
      Component statusText = CURRENT_STATUS.copy().append(": ").append(status);
      graphics.drawString(this.font, statusText, 20, this.height - 60, 0xFFFFFF);

      // 如果启用了镜像，显示当前实际使用的域名，方便排查配置是否生效
      if (MirrorConfig.isEnabled()) {
         String domain = MirrorConfig.getActiveDomain();
         if (!domain.isBlank()) {
            Component domainText = Component.literal("URL: ").append(
               Component.literal(domain).withStyle(ChatFormatting.YELLOW)
            );
            graphics.drawString(this.font, domainText, 20, this.height - 48, 0xFFFFFF);
         }
      }

      // 绘制标签：只有处于"启用 + 自定义模式"时才提示输入框的用途
      if (this.tempMode == MirrorMode.CUSTOM && this.tempEnabled) {
         graphics.drawString(this.font, CUSTOM_DOMAIN_LABEL, centerX - 150, 135, 0xFFFFFF);
      }
   }

   @Override
   public void resize(Minecraft minecraft, int width, int height) {
      super.resize(minecraft, width, height);
      // 重新初始化以按新尺寸调整控件布局
      this.init();
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      // 喵~防御：ESC(键码 256) 直接返回上一界面，等同于点"返回"，不保存改动
      if (keyCode == 256) {
         this.onClose();
         return true;
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
   }
}
