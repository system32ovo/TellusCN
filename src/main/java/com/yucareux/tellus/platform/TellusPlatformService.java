package com.yucareux.tellus.platform;

import com.yucareux.tellus.network.GeoTpOpenMapPayload;
import com.yucareux.tellus.network.ManagedTerrainStatusPayload;
import com.yucareux.tellus.network.TellusWeatherPayload;
import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;

public interface TellusPlatformService {
   Path gameDir();

   Path configDir();

   boolean isModLoaded(String modId);

   void sendWeatherPayload(ServerPlayer player, TellusWeatherPayload payload);

   void sendGeoTpOpenMapPayload(ServerPlayer player, GeoTpOpenMapPayload payload);

   void sendManagedTerrainStatusPayload(ServerPlayer player, ManagedTerrainStatusPayload payload);

   void registerDistantHorizonsLifecycle(Runnable onServerStart, Runnable onServerStop, Runnable onPlayerJoin);
}
