package com.yucareux.tellus;

import com.yucareux.tellus.client.screen.EarthTeleportScreen;
import com.yucareux.tellus.client.hud.ManagedTerrainDownloadOverlay;
import com.yucareux.tellus.integration.distant_horizons.managed.ManagedTerrainClientState;
import com.yucareux.tellus.integration.distant_horizons.managed.ManagedTerrainViewDistance;
import com.yucareux.tellus.network.GeoTpOpenMapPayload;
import com.yucareux.tellus.network.GeoTpTeleportPayload;
import com.yucareux.tellus.network.ManagedTerrainStatusPayload;
import com.yucareux.tellus.network.ManagedTerrainViewPayload;
import com.yucareux.tellus.network.TellusWeatherPayload;
import com.yucareux.tellus.platform.TellusClientPlatform;
import com.yucareux.tellus.world.realtime.SnowGrid;
import com.yucareux.tellus.world.realtime.TemperatureGrid;
import com.yucareux.tellus.world.realtime.TellusRealtimeState;
import java.util.Objects;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class TellusClient implements ClientModInitializer {
   private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Tellus.id("controls"));
   private static final KeyMapping OPEN_MAP_KEY = new KeyMapping(
      "key.tellus.open_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, KEY_CATEGORY
   );
   private int managedTerrainViewUpdateTicks;

   @Override
   public void onInitializeClient() {
      TellusClientPlatform.configureGeoTeleport(
         () -> ClientPlayNetworking.canSend(GeoTpTeleportPayload.TYPE),
         (latitude, longitude) -> ClientPlayNetworking.send(new GeoTpTeleportPayload(latitude, longitude))
      );
      KeyMappingHelper.registerKeyMapping(OPEN_MAP_KEY);
      HudElementRegistry.addLast(Tellus.id("managed_terrain_status"), (graphics, deltaTracker) -> ManagedTerrainDownloadOverlay.render(graphics));
      ClientPlayNetworking.registerGlobalReceiver(Objects.requireNonNull(GeoTpOpenMapPayload.TYPE, "GeoTpOpenMapPayload.TYPE"), (payload, context) -> context.client().execute(() -> {
         Minecraft minecraft = context.client();
         Screen parent = minecraft.gui.screen();
         minecraft.gui.setScreen(new EarthTeleportScreen(parent, payload.latitude(), payload.longitude()));
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         Objects.requireNonNull(TellusWeatherPayload.TYPE, "TellusWeatherPayload.TYPE"),
         (payload, context) -> context.client()
            .execute(
               () -> {
                  SnowGrid grid = payload.historicalSnowEnabled() && payload.spacingBlocks() > 0
                     ? new SnowGrid(payload.centerX(), payload.centerZ(), payload.spacingBlocks(), payload.snowIndex())
                     : SnowGrid.empty();
                  TemperatureGrid temperatureGrid = payload.spacingBlocks() > 0
                     ? new TemperatureGrid(
                        payload.centerX(),
                        payload.centerZ(),
                        payload.spacingBlocks(),
                        payload.temperatureC(),
                        System.currentTimeMillis() - Math.max(0L, payload.temperatureAgeMs())
                     )
                     : TemperatureGrid.empty();
                  TellusRealtimeState.updateWeatherState(
                     payload.weatherEnabled(), payload.precipitationMode(), payload.historicalSnowEnabled(), grid, temperatureGrid
                  );
               }
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(
         Objects.requireNonNull(ManagedTerrainStatusPayload.TYPE, "ManagedTerrainStatusPayload.TYPE"),
         (payload, context) -> context.client().execute(() -> ManagedTerrainClientState.update(payload.status()))
      );
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         while (OPEN_MAP_KEY.consumeClick()) {
            if (client.gui.screen() == null && client.player != null && client.getConnection() != null) {
               client.getConnection().sendCommand("tellus map");
            }
         }

         if (client.player != null && ++this.managedTerrainViewUpdateTicks >= 40) {
            this.managedTerrainViewUpdateTicks = 0;
            if (ClientPlayNetworking.canSend(ManagedTerrainViewPayload.TYPE)) {
               ClientPlayNetworking.send(new ManagedTerrainViewPayload(ManagedTerrainViewDistance.detect()));
            }
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
         TellusRealtimeState.reset();
         ManagedTerrainClientState.reset();
         this.managedTerrainViewUpdateTicks = 0;
      });
   }
}
