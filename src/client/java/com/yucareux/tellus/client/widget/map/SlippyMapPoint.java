package com.yucareux.tellus.client.widget.map;

import net.minecraft.util.Mth;

public class SlippyMapPoint {
   private static final double MIN_LATITUDE = -85.05112878;
   private static final double MAX_LATITUDE = 85.05112878;

   private final double latitude;
   private final double longitude;

   public SlippyMapPoint(double latitude, double longitude) {
      this.latitude = Mth.clamp(latitude, MIN_LATITUDE, MAX_LATITUDE);
      this.longitude = longitude;
   }

   public SlippyMapPoint(int x, int y, int zoom) {
      double maximumX = 256 * (1 << zoom);
      this.longitude = x / maximumX * 360.0 - 180.0;
      double maximumY = 256 * (1 << zoom);
      this.latitude = Math.toDegrees(Math.atan(Math.sinh(Math.PI - (Math.PI * 2) * y / maximumY)));
   }

   public double getLatitude() {
      return this.latitude;
   }

   public double getLongitude() {
      return this.longitude;
   }

   public int getX(int zoom) {
      double maximumX = 256 * (1 << zoom);
      return Mth.floor((this.longitude + 180.0) / 360.0 * maximumX);
   }

   public int getY(int zoom) {
      double maximumY = 256 * (1 << zoom);
      double angle = Math.toRadians(this.latitude);
      return Mth.floor((1.0 - Math.log(Math.tan(angle) + 1.0 / Math.cos(angle)) / Math.PI) / 2.0 * maximumY);
   }

   public SlippyMapPoint translate(int x, int y, int zoom) {
      int currentX = this.getX(zoom);
      int currentY = this.getY(zoom);
      return new SlippyMapPoint(currentX + x, currentY + y, zoom);
   }
}
