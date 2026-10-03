package com.yucareux.tellus.integration.distant_horizons.managed;

public record ManagedTerrainCell(int x, int z) {
   public static final int SIZE_CHUNKS = 32;

   public static ManagedTerrainCell containingChunk(int chunkX, int chunkZ) {
      return new ManagedTerrainCell(Math.floorDiv(chunkX, SIZE_CHUNKS), Math.floorDiv(chunkZ, SIZE_CHUNKS));
   }

   public int minChunkX() {
      return this.x * SIZE_CHUNKS;
   }

   public int minChunkZ() {
      return this.z * SIZE_CHUNKS;
   }

   public int maxChunkX() {
      return this.minChunkX() + SIZE_CHUNKS - 1;
   }

   public int maxChunkZ() {
      return this.minChunkZ() + SIZE_CHUNKS - 1;
   }

   public long packed() {
      return (long)this.x << 32 | this.z & 0xffffffffL;
   }
}
