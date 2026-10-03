package com.yucareux.tellus.world.realtime;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;

/** Minecraft 26.2 time, weather, and game-rule bridge. */
final class RealtimeVersionCompat {
   private RealtimeVersionCompat() {
   }

   static long dayTimeTicks(ServerLevel level) {
      return defaultClock(level).map(clock -> level.clockManager().getTotalTicks(clock)).orElse(level.getGameTime());
   }

   static void setDayTimeTicks(ServerLevel level, long ticks) {
      defaultClock(level).ifPresent(clock -> level.clockManager().setTotalTicks(clock, ticks));
   }

   static void applyWeather(ServerLevel level, boolean raining, boolean thundering) {
      WeatherData weather = level.getWeatherData();
      weather.setClearWeatherTime(0);
      weather.setRainTime(6000);
      weather.setRaining(raining);
      weather.setThunderTime(6000);
      weather.setThundering(thundering);
   }

   static Boolean daylightCycle(ServerLevel level) {
      return (Boolean)level.getGameRules().get(GameRules.ADVANCE_TIME);
   }

   static Integer sleepingPercentage(ServerLevel level) {
      return (Integer)level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
   }

   static Boolean weatherCycle(ServerLevel level) {
      return (Boolean)level.getGameRules().get(GameRules.ADVANCE_WEATHER);
   }

   static void setDaylightCycle(ServerLevel level, MinecraftServer server, boolean value) {
      level.getGameRules().set(GameRules.ADVANCE_TIME, value, server);
   }

   static void setSleepingPercentage(ServerLevel level, MinecraftServer server, int value) {
      level.getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, value, server);
   }

   static void setWeatherCycle(ServerLevel level, MinecraftServer server, boolean value) {
      level.getGameRules().set(GameRules.ADVANCE_WEATHER, value, server);
   }

   private static Optional<Holder<WorldClock>> defaultClock(ServerLevel level) {
      return level.dimensionTypeRegistration().value().defaultClock();
   }
}
