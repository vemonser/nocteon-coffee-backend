package com.nocteon.nocteon_api.userActivity.resolver;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.userActivity.enums.DeviceType;

@Component
public class DeviceTypeResolver {

    public DeviceType resolve(String userAgent) {
        if (userAgent == null) {
            return DeviceType.OTHER;
        }

        String ua = userAgent.toLowerCase();

        if (ua.contains("windows")) return DeviceType.WINDOWS;
        if (ua.contains("mac os") || ua.contains("macintosh")) return DeviceType.MAC;
        if (ua.contains("android")) return DeviceType.ANDROID;
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return DeviceType.IOS;
        if (ua.contains("linux")) return DeviceType.LINUX;

        return DeviceType.OTHER;
    }
}