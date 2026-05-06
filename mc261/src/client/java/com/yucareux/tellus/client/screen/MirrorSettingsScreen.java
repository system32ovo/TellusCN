package com.yucareux.tellus.client.screen;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.config.MirrorConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import java.util.Objects;

/**
 * TellusCN 镜像设置界面 (Minecraft 26.1 兼容版本)
 *
 * 为中国玩家提供便捷的数据源镜像配置
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

   private final Screen parent;

   // UI 组件 (mc261 使用 Button 替代 Checkbox 和 CycleButton)
   private Button enableButton;
   private Button officialModeButton;
   private Button customModeButton;
   private EditBox customDomainEditBox;
   private Button saveButton;

   // 临时状态
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

      // 判断当前模式
      if (!this.tempCustomDomain.isBlank()) {
         this.tempMode = MirrorMode.CUSTOM;
      } else {
         this.tempMode = MirrorMode.OFFICIAL;
      }
   }

   @Override
   protected void init() {
      int centerX = this.width / 2;
      int startY = 60;
      int lineHeight = 25;

      // 启用/禁用镜像按钮 (替代 Checkbox)
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

      this.updateUIState();
   }

   private void updateUIState() {
      // 更新模式按钮状态
      this.officialModeButton.active = this.tempEnabled && this.tempMode != MirrorMode.OFFICIAL;
      this.customModeButton.active = this.tempEnabled && this.tempMode != MirrorMode.CUSTOM;

      // 更新输入框状态
      this.customDomainEditBox.active = this.tempEnabled && this.tempMode == MirrorMode.CUSTOM;

      // 更新启用按钮样式
      if (this.tempEnabled) {
         this.enableButton.setMessage(DISABLE_MIRROR);
      } else {
         this.enableButton.setMessage(ENABLE_MIRROR);
      }
   }

   private void saveAndClose() {
      // 保存配置
      MirrorConfig.setEnabled(this.tempEnabled);

      if (this.tempEnabled) {
         if (this.tempMode == MirrorMode.CUSTOM) {
            String domain = this.customDomainEditBox.getValue().trim();
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
         this.minecraft.setScreen(this.parent);
      }
   }

   // mc261 使用 extractRenderState 替代 render
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);

      int centerX = this.width / 2;

      // 绘制标题
      graphics.centeredText(this.font, this.title, centerX, 20, 0xFFFFFF);

      // 绘制说明
      graphics.centeredText(this.font, DESCRIPTION, centerX, 40, 0xAAAAAA);

      // 绘制当前状态
      Component status = MirrorConfig.isEnabled() ? STATUS_ENABLED : STATUS_DISABLED;
      Component statusText = CURRENT_STATUS.copy().append(": ").append(status);
      graphics.text(this.font, statusText, 20, this.height - 60, 0xFFFFFF);

      // 如果启用了镜像，显示当前使用的域名
      if (MirrorConfig.isEnabled()) {
         String domain = MirrorConfig.getActiveDomain();
         if (!domain.isBlank()) {
            Component domainText = Component.literal("URL: ").append(
               Component.literal(domain).withStyle(ChatFormatting.YELLOW)
            );
            graphics.text(this.font, domainText, 20, this.height - 48, 0xFFFFFF);
         }
      }

      // 绘制标签
      if (this.tempMode == MirrorMode.CUSTOM && this.tempEnabled) {
         graphics.text(this.font, CUSTOM_DOMAIN_LABEL, centerX - 150, 135, 0xFFFFFF);
      }
   }

   // mc261 使用 extractBackground 替代 renderBackground
   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, -1072689136);
   }
}
