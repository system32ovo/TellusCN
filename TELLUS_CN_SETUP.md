# TellusCN 中国版配置指南

本文档说明如何为 Tellus Mod 配置国内可用的数据源，解决国内玩家访问国际数据源缓慢或无法访问的问题。

## 已修改的文件

以下文件已添加系统属性支持，可通过 JVM 参数配置自定义端点：

### 1. 高程数据源 (Elevation)

| 数据源 | 系统属性 | 默认值 |
|--------|----------|--------|
| Terrarium 高程瓦片 | `tellus.elevation.endpoint` | `https://s3.amazonaws.com/elevation-tiles-prod/terrarium` |
| Copernicus DEM 30m | `tellus.copernicus30.endpoint` | `https://copernicus-dem-30m.s3.eu-central-1.amazonaws.com` |
| Copernicus DEM 90m | `tellus.copernicus90.endpoint` | `https://copernicus-dem-90m.s3.eu-central-1.amazonaws.com` |
| USGS 3DEP | `tellus.usgs.endpoint` | `https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer/exportImage` |
| Japan GSI | `tellus.japangsi.endpoint` | `https://cyberjapandata.gsi.go.jp/xyz` |
| ArcticDEM | `tellus.arcticdem.endpoint` | `https://pgc-opendata-dems.s3.us-west-2.amazonaws.com/arcticdem/mosaics/v4.1` |
| REMA (南极) | `tellus.rema.endpoint` | `https://pgc-opendata-dems.s3.us-west-2.amazonaws.com/rema/mosaics/v2.0` |

### 2. 地表覆盖数据源 (Land Cover)

| 数据源 | 系统属性 | 默认值 |
|--------|----------|--------|
| ESA WorldCover | `tellus.landcover.endpoint` | `https://esa-worldcover.s3.eu-central-1.amazonaws.com/v200/2021/map` |

### 3. 天气数据源 (Weather)

| 数据源 | 系统属性 | 默认值 |
|--------|----------|--------|
| Open-Meteo API | `tellus.weather.endpoint` | `https://api.open-meteo.com/v1` |

### 4. 地理编码数据源 (Geocoding)

| 数据源 | 系统属性 | 默认值 |
|--------|----------|--------|
| Nominatim | `tellus.geocoding.endpoint` | `https://nominatim.openstreetmap.org` |

### 5. OSM 数据源

| 数据源 | 系统属性 | 默认值 |
|--------|----------|--------|
| Overpass API | `tellus.osm.overpass.endpoints` | 多个端点逗号分隔 |

### 6. 土地掩码数据源

| 数据源 | 系统属性 | 默认值 |
|--------|----------|--------|
| Land Mask | `tellus.landmask.baseUrl` | `https://github.com/Yucareux/Tellus-Land-Polygons/releases/download/v1.0.0/` |

### 7. 通用 S3 反代

| 数据源 | 系统属性 | 默认值 |
|--------|----------|--------|
| S3 通用反代 | `tellus.s3proxy.endpoint` | - |

> **注意**: `tellus.s3proxy.endpoint` 用于 ArcticDEM 等数据源内部动态构建的 S3 URL，指向你的 Workers `/s3/` 路由。

## 使用方法

### 方法一：启动参数配置（推荐）

在启动 Minecraft 时，通过 JVM 参数指定国内镜像：

```bash
# 示例：使用国内镜像（假设你有可用的镜像服务器）
java -Dtellus.elevation.endpoint=https://your-mirror.com/terrarium \
     -Dtellus.landcover.endpoint=https://your-mirror.com/worldcover \
     -Dtellus.weather.endpoint=https://your-mirror.com/weather \
     -Dtellus.geocoding.endpoint=https://your-mirror.com/nominatim \
     -Dtellus.osm.overpass.endpoints=https://your-mirror.com/overpass \
     -Dtellus.landmask.baseUrl=https://your-mirror.com/landmask/ \
     -jar minecraft.jar
```

### 方法二：使用代理

如果无法搭建镜像，可以通过代理访问：

```bash
# 设置 HTTP/HTTPS 代理
java -Dhttp.proxyHost=proxy.example.com \
     -Dhttp.proxyPort=8080 \
     -Dhttps.proxyHost=proxy.example.com \
     -Dhttps.proxyPort=8080 \
     -jar minecraft.jar
```

### 方法三：使用启动器配置

对于 HMCL、PCL2 等启动器：

1. 打开启动器设置
2. 找到 "Java 虚拟机参数" 或 "JVM 参数"
3. 添加以下参数：

```
-Dtellus.elevation.endpoint=https://your-mirror.com/terrarium
-Dtellus.landcover.endpoint=https://your-mirror.com/worldcover
-Dtellus.weather.endpoint=https://your-mirror.com/weather
-Dtellus.geocoding.endpoint=https://your-mirror.com/nominatim
```

## 推荐的国内替代方案

### 高程数据
- **Mapbox 中国**: 需要申请 API Key
- **高德地图**: 提供高程数据 API
- **百度地图**: 提供高程数据 API
- **自建 CDN**: 将 AWS S3 数据同步到国内对象存储（阿里云 OSS、腾讯云 COS）

### 地表覆盖数据
- **全球地表覆盖数据**: 可以从中国科学院资源环境科学与数据中心获取
- **自建镜像**: 将 ESA WorldCover 数据同步到国内服务器

### 天气数据
- **和风天气**: `https://devapi.qweather.com`（需要 API Key）
- **心知天气**: `https://api.seniverse.com`（需要 API Key）

### 地理编码
- **高德地理编码**: `https://restapi.amap.com/v3/geocode/geo`（需要 API Key）
- **百度地理编码**: `https://api.map.baidu.com/geocoding/v3`（需要 API Key）

### OSM 数据
- **Overpass 国内镜像**: 可以搭建或使用社区提供的镜像
- **Geofabrik 中国数据**: 定期下载中国区域 OSM 数据

## 数据缓存

Tellus Mod 会自动缓存下载的数据到以下目录：

```
.minecraft/tellus/cache/
├── elevation-tellus/      # Terrarium 高程数据
├── elevation-copernicus/  # Copernicus DEM 数据
├── elevation-usgs3dep/    # USGS 高程数据
├── elevation-japangsi/    # 日本 GSI 数据
├── worldcover2021/        # ESA 地表覆盖数据
└── ...
```

缓存可以显著减少重复下载，建议定期备份缓存目录。

## 注意事项

1. **数据一致性**: 使用镜像时，请确保镜像数据与原始数据源保持一致
2. **API 限制**: 部分国内 API 有调用频率限制，需要申请 Key
3. **坐标系转换**: 国内地图服务通常使用 GCJ-02 坐标系，需要转换为 WGS-84
4. **数据更新**: 定期检查数据源更新，确保使用最新数据

## 技术支持

如有问题，请联系：
- GitHub Issues: https://github.com/Yucareux/Tellus/issues
- 国内社区: [待补充]

## 许可证

本配置修改遵循原项目的开源许可证。
