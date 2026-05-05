package com.yucareux.tellus.client.screen;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.config.MirrorConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import java.util.Objects;

/**
 * TellusCN 镜像设置界面
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
   
   private static final Component CANCEL_BUTTON = Objects.requireNonNull(
      Component.translatable("gui.cancel"),
      "cancelButton"
   );
   
   private static final Component BACK_BUTTON = Objects.requireNonNull(
      Component.translatable("gui.back"),
      "backButton"
   );
   
   private final Screen parent;
   
   // UI 组件
   private Checkbox enableCheckbox;
   private CycleButton<MirrorMode> modeButton;
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
      
      // 标题和说明
      // 说明文字在 render 中绘制
      
      // 启用镜像复选框
      this.enableCheckbox = Checkbox.builder(ENABLE_MIRROR, this.font)
         .pos(centerX - 150, startY)
         .selected(this.tempEnabled)
         .build();
      this.addRenderableWidget(this.enableCheckbox);
      
      // 模式选择按钮（官方预设 / 自定义）
      this.modeButton = CycleButton.<MirrorMode>builder(mode -> {
            return mode == MirrorMode.OFFICIAL ? USE_OFFICIAL : USE_CUSTOM;
         })
         .withValues(MirrorMode.OFFICIAL, MirrorMode.CUSTOM)
         .withInitialValue(this.tempMode)
         .create(centerX - 150, startY + lineHeight * 1.5, 300, 20, Component.empty(),
            (button, mode) -> {
               this.tempMode = mode;
               this.updateUIState();
            });
      this.addRenderableWidget(this.modeButton);
      
      // 官方预设选择（仅在官方模式下显示）
      // 简化处理：使用 modeButton 切换，实际预设通过配置管理
      
      // 自定义域名输入框
      this.customDomainEditBox = new EditBox(
         this.font,
         centerX - 150, startY + lineHeight * 3, 300, 20,
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
      boolean enabled = this.enableCheckbox.selected();
      boolean isCustom = this.tempMode == MirrorMode.CUSTOM;
      
      this.modeButton.active = enabled;
      this.customDomainEditBox.active = enabled && isCustom;
      this.saveButton.active = true;
   }
   
   private void saveAndClose() {
      // 保存配置
      boolean enabled = this.enableCheckbox.selected();
      MirrorConfig.setEnabled(enabled);
      
      if (enabled) {
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
         enabled, this.tempMode, this.customDomainEditBox.getValue());
      
      this.onClose();
   }
   
   @Override
   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }
   }
   
   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics, mouseX, mouseY, partialTick);
      
      int centerX = this.width / 2;
      
      // 绘制标题
      graphics.drawCenteredString(this.font, this.title, centerX, 20, 0xFFFFFF);
      
      // 绘制说明
      graphics.drawCenteredString(this.font, DESCRIPTION, centerX, 40, 0xAAAAAA);
      
      // 绘制当前状态
      Component status = MirrorConfig.isEnabled() ? STATUS_ENABLED : STATUS_DISABLED;
      Component statusText = CURRENT_STATUS.copy().append(": ").append(status);
      graphics.drawString(this.font, statusText, 20, this.height - 60, 0xFFFFFF);
      
      // 如果启用了镜像，显示当前使用的域名
      if (MirrorConfig.isEnabled()) {
         String domain = MirrorConfig.getActiveDomain();
         if (!domain.isBlank()) {
            Component domainText = Component.literal("URL: ").append(
               Component.literal(domain).withStyle(ChatFormatting.YELLOW)
            );
            graphics.drawString(this.font, domainText, 20, this.height - 48, 0xFFFFFF);
         }
      }
      
      // 绘制标签
      if (this.tempMode == MirrorMode.CUSTOM && this.enableCheckbox.selected()) {
         graphics.drawString(this.font, CUSTOM_DOMAIN_LABEL, centerX - 150, 105, 0xFFFFFF);
      }
      
      super.render(graphics, mouseX, mouseY, partialTick);
   }
   
   @Override
   public void resize(int width, int height) {
      super.resize(width, height);
      this.init(); // 重新初始化以调整布局
   }
   
   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256) { // ESC 键
         this.onClose();
         return true;
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
   }
}
