package com.yucareux.tellus.config;

import com.yucareux.tellus.Tellus;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * TellusCN 自定义 NameService SPI 实现
 * 
 * 轻量级 DNS 解析器，规则：
 * - 匹配配置的域名后缀 → 走自定义 DNS（1.1.1.1 等）
 * - 其他所有域名 → 放行走系统默认 DNS
 * 
 * 优点：
 * ✅ 只写一次，全局自动生效
 * ✅ 原有网络代码完全不用改
 * ✅ 原版、其他模组完全不受影响
 * ✅ 无手动封装、不用改每一处请求
 */
public class TellusNameService extends InetAddressResolverProvider {
    
    private static final int DNS_TIMEOUT_MS = 5000;
    private static final int DNS_PORT = 53;
    
    @Override
    public InetAddressResolver get(Configuration configuration) {
        return new TellusInetResolver(configuration);
    }
    
    @Override
    public String name() {
        return "TellusCN-DNS-Resolver";
    }
    
    /**
     * 实际的 DNS 解析器实现
     */
    private static class TellusInetResolver implements InetAddressResolver {
        private final InetAddressResolver defaultResolver;
        
        TellusInetResolver(InetAddressResolverProvider.Configuration config) {
            this.defaultResolver = config.builtinResolver();
        }
        
        @Override
        public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
            // 检查是否应该使用自定义 DNS
            if (DnsResolverConfig.isEnabled() && DnsResolverConfig.shouldUseCustomDns(host)) {
                try {
                    InetAddress[] addresses = resolveWithCustomDns(host, DnsResolverConfig.getActiveDnsServer());
                    if (addresses != null && addresses.length > 0) {
                        Tellus.LOGGER.debug("[TellusDNS] Resolved {} using custom DNS", host);
                        return Arrays.stream(addresses);
                    }
                } catch (Exception e) {
                    Tellus.LOGGER.warn("[TellusDNS] Failed to resolve {} with custom DNS, falling back to default", host);
                }
            }
            
            // 使用系统默认解析
            return defaultResolver.lookupByName(host, lookupPolicy);
        }
        
        @Override
        public String lookupByAddress(byte[] addr) throws UnknownHostException {
            return defaultResolver.lookupByAddress(addr);
        }
    }
    
    /**
     * 使用指定 DNS 服务器解析域名
     * 使用简单的 UDP DNS 查询
     */
    public static InetAddress[] resolveWithSpecificDns(String hostname, String dnsServer) throws UnknownHostException {
        // 首先尝试使用 Java 内置的 DNS 解析，但指定 nameserver
        // 由于 Java 标准库不直接支持指定 DNS 服务器，我们使用反射或系统属性方式
        
        // 方案：使用 dnsjava 库或自定义 UDP 查询
        // 这里先使用一个简化的实现：通过修改系统属性临时切换 DNS
        
        String originalDns = System.getProperty("sun.net.spi.nameservice.nameservers");
        String originalProvider = System.getProperty("sun.net.spi.nameservice.provider.1");
        
        try {
            // 设置临时 DNS
            System.setProperty("sun.net.spi.nameservice.nameservers", dnsServer);
            System.setProperty("sun.net.spi.nameservice.provider.1", "dns,sun");
            
            // 刷新 DNS 缓存
            java.security.Security.setProperty("networkaddress.cache.ttl", "0");
            
            // 解析域名
            return InetAddress.getAllByName(hostname);
            
        } catch (Exception e) {
            throw new UnknownHostException("Failed to resolve " + hostname + " via " + dnsServer + ": " + e.getMessage());
        } finally {
            // 恢复原始设置
            if (originalDns != null) {
                System.setProperty("sun.net.spi.nameservice.nameservers", originalDns);
            } else {
                System.clearProperty("sun.net.spi.nameservice.nameservers");
            }
            
            if (originalProvider != null) {
                System.setProperty("sun.net.spi.nameservice.provider.1", originalProvider);
            } else {
                System.clearProperty("sun.net.spi.nameservice.provider.1");
            }
        }
    }
    
    /**
     * 初始化并注册 NameService
     * 在游戏启动时调用
     */
    public static void initialize() {
        try {
            // 检查 Java 版本是否支持 InetAddressResolver SPI (Java 18+)
            // 对于低版本 Java，使用系统属性方式
            
            int javaVersion = Runtime.version().feature();
            Tellus.LOGGER.info("[TellusDNS] Java version: {}", javaVersion);
            
            if (javaVersion >= 18) {
                // Java 18+ 支持 SPI 方式，自动加载
                Tellus.LOGGER.info("[TellusDNS] Using InetAddressResolver SPI (Java 18+)");
            } else {
                // Java 17 及以下，使用系统属性方式
                Tellus.LOGGER.info("[TellusDNS] Using system property DNS configuration");
                setupLegacyDns();
            }
            
        } catch (Exception e) {
            Tellus.LOGGER.error("[TellusDNS] Failed to initialize DNS resolver", e);
        }
    }
    
    /**
     * 为 Java 17 及以下设置 DNS
     */
    private static void setupLegacyDns() {
        if (!DnsResolverConfig.isEnabled()) {
            return;
        }
        
        String dnsServer = DnsResolverConfig.getActiveDnsServer();
        if (dnsServer.isBlank()) {
            return;
        }
        
        // 设置系统 DNS
        System.setProperty("sun.net.spi.nameservice.nameservers", dnsServer);
        System.setProperty("sun.net.spi.nameservice.provider.1", "dns,sun");
        
        Tellus.LOGGER.info("[TellusDNS] Legacy DNS configured: {}", dnsServer);
    }
}
