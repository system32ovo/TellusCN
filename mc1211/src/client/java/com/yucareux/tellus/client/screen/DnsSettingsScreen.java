package com.yucareux.tellus.client.screen;

import com.yucareux.tellus.config.DnsResolverConfig;
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
 * TellusCN DNS 设置界面
 * 
 * 为中国玩家提供自定义 DNS 解析配置
 * 解决 workers.dev 等域名被 DNS 污染的问题
 */
@Environment(EnvType.CLIENT)
public class DnsSettingsScreen extends Screen {
   
   private static final Component TITLE = Objects.requireNonNull(
      Component.translatable("tellus.dns_settings.title").withStyle(ChatFormatting.BOLD),
      "title"
   );
   
   private static final Component DESCRIPTION = Objects.requireNonNull(
      Component.translatable("tellus.dns_settings.description"),
      "description"
   );
   
   private static final Component ENABLE_DNS = Objects.requireNonNull(
      Component.translatable("tellus.dns_settings.enable"),
      "enableDns"
   );
   
   private static final Component DNS_PRESET_LABEL = Objects.requireNonNull(
      Component.translatable("tellus.dns_settings.preset_label"),
      "dnsPresetLabel"
   );
   
   private static final Component CUSTOM_DNS_LABEL = Objects.requireNonNull(
      Component.translatable("tellus.dns_settings.custom_label"),
      "customDnsLabel"
   );
   
   private static final Component CUSTOM_DNS_HINT = Objects.requireNonNull(
      Component.translatable("tellus.dns_settings.custom_hint"),
      "customDnsHint"
   );
   
   private static final Component SAVE_BUTTON = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.save"),
      "saveButton"
   );
   
   private static final Component BACK_BUTTON = Objects.requireNonNull(
      Component.translatable("tellus.mirror_settings.back"),
      "backButton"
   );
   
   private final Screen parent;
   
   // UI 组件
   private Checkbox enableCheckbox;
   private CycleButton<DnsMode> modeButton;
   private EditBox customDnsEditBox;
   private Button saveButton;
   
   // 临时状态
   private boolean tempEnabled;
   private DnsMode tempMode;
   private String tempCustomDns;
   private int tempPresetIndex;
   
   private enum DnsMode {
      PRESET,   // 使用预设 DNS
      CUSTOM    // 使用自定义 DNS
   }
   
   public DnsSettingsScreen(Screen parent) {
      super(TITLE);
      this.parent = parent;
      
      // 加载当前配置到临时状态
      this.tempEnabled = DnsResolverConfig.isEnabled();
      this.tempCustomDns = DnsResolverConfig.getCustomDns();
      this.tempPresetIndex = DnsResolverConfig.getSelectedDns();
      
      // 判断当前模式
      if (!this.tempCustomDns.isBlank()) {
         this.tempMode = DnsMode.CUSTOM;
      } else {
         this.tempMode = DnsMode.PRESET;
      }
   }
   
   @Override
   protected void init() {
      int centerX = this.width / 2;
      int startY = 60;
      int lineHeight = 25;
      
      // 启用 DNS 复选框
      this.enableCheckbox = new Checkbox(
         centerX - 150, startY, 300, 20,
         ENABLE_DNS,
         this.tempEnabled
      );
      this.addRenderableWidget(this.enableCheckbox);
      
      // 模式选择按钮（预设 / 自定义）
      this.modeButton = CycleButton.<DnsMode>builder(mode -> {
            return mode == DnsMode.PRESET 
               ? Component.translatable("tellus.dns_settings.use_preset")
               : Component.translatable("tellus.dns_settings.use_custom");
         })
         .withValues(DnsMode.PRESET, DnsMode.CUSTOM)
         .withInitialValue(this.tempMode)
         .create(centerX - 150, startY + lineHeight * 2, 300, 20,
            Component.empty(), (button, mode) -> {
               this.tempMode = mode;
               this.updateUIState();
            });
      this.addRenderableWidget(this.modeButton);
      
      // 预设 DNS 选择按钮
      String[] presets = DnsResolverConfig.getDnsPresets();
      if (tempPresetIndex >= presets.length) {
         tempPresetIndex = 0;
      }
      
      this.addRenderableWidget(
         CycleButton.<Integer>builder(index -> Component.literal(presets[index]))
            .withValues(0, 1, 2, 3, 4)
            .withInitialValue(this.tempPresetIndex)
            .create(centerX - 150, startY + lineHeight * 3, 300, 20,
               DNS_PRESET_LABEL, (button, index) -> {
                  this.tempPresetIndex = index;
               })
      );
      
      // 自定义 DNS 输入框
      this.customDnsEditBox = new EditBox(
         this.font,
         centerX - 150, startY + lineHeight * 4, 300, 20,
         CUSTOM_DNS_LABEL
      );
      this.customDnsEditBox.setValue(this.tempCustomDns);
      this.customDnsEditBox.setHint(CUSTOM_DNS_HINT);
      this.customDnsEditBox.setMaxLength(50);
      this.addRenderableWidget(this.customDnsEditBox);
      
      // 保存按钮
      this.saveButton = Button.builder(SAVE_BUTTON, button -> this.saveAndClose())
         .bounds(centerX - 155, this.height - 30, 150, 20)
         .build();
      this.addRenderableWidget(this.saveButton);
      
      // 返回按钮
      this.addRenderableWidget(Button.builder(BACK_BUTTON, button -> this.onClose())
         .bounds(centerX + 5, this.height - 30, 150, 20)
         .build());
      
      this.updateUIState();
   }
   
   private void updateUIState() {
      boolean enabled = this.enableCheckbox.selected();
      boolean isCustom = this.tempMode == DnsMode.CUSTOM;
      
      this.modeButton.active = enabled;
      this.customDnsEditBox.active = enabled && isCustom;
      this.saveButton.active = true;
   }
   
   private void saveAndClose() {
      // 保存配置
      boolean enabled = this.enableCheckbox.selected();
      DnsResolverConfig.setEnabled(enabled);
      
      if (enabled) {
         if (this.tempMode == DnsMode.CUSTOM) {
            String dns = this.customDnsEditBox.getValue().trim();
            DnsResolverConfig.setCustomDns(dns);
         } else {
            // 使用预设，清空自定义
            DnsResolverConfig.setCustomDns("");
            DnsResolverConfig.setSelectedDns(this.tempPresetIndex);
         }
      }
      
      // 重新初始化 DNS
      com.yucareux.tellus.config.TellusNameService.initialize();
      
      this.onClose();
   }
   
   @Override
   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }
   
   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics);
      super.render(graphics, mouseX, mouseY, partialTick);
      
      int centerX = this.width / 2;
      
      // 标题
      graphics.drawCenteredString(this.font, TITLE, centerX, 20, 0xFFFFFF);
      
      // 说明文字
      graphics.drawCenteredString(this.font, DESCRIPTION, centerX, 40, 0xAAAAAA);
      
      // 当前状态
      if (this.enableCheckbox.selected()) {
         String dns = this.tempMode == DnsMode.CUSTOM 
            ? this.customDnsEditBox.getValue().trim()
            : DnsResolverConfig.getDnsPresets()[this.tempPresetIndex];
         
         Component status = Component.translatable("tellus.dns_settings.current_dns", dns)
            .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN));
         graphics.drawCenteredString(this.font, status, centerX, 170, 0xFFFFFF);
      }
   }
}
