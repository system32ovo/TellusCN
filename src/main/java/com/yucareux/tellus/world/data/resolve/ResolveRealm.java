package com.yucareux.tellus.world.data.resolve;

import java.util.Locale;

/** Biogeographic realms used by RESOLVE Ecoregions 2017. */
public enum ResolveRealm {
   AFROTROPIC,
   ANTARCTIC,
   AUSTRALASIA,
   INDOMALAYAN,
   NEARCTIC,
   NEOTROPIC,
   OCEANIA,
   PALEARCTIC,
   NONE,
   UNKNOWN;

   public static ResolveRealm fromSource(String value) {
      if (value == null || value.isBlank()) {
         return UNKNOWN;
      }

      return switch (value.trim().toLowerCase(Locale.ROOT)) {
         case "afrotropic" -> AFROTROPIC;
         case "antarctica", "antarctic" -> ANTARCTIC;
         case "australasia" -> AUSTRALASIA;
         case "indomalayan", "indo-malay" -> INDOMALAYAN;
         case "nearctic" -> NEARCTIC;
         case "neotropic" -> NEOTROPIC;
         case "oceania" -> OCEANIA;
         case "palearctic" -> PALEARCTIC;
         case "n/a", "none" -> NONE;
         default -> UNKNOWN;
      };
   }
}
