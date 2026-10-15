package com.aiasti.tiger.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 本地接口的跨源配置（BIZ-013）。
 *
 * <p>桌面壳与本地服务不同源：开发期渲染层来自 Vite dev server（`http://localhost:5173`），
 * 打包期来自 `file://` 页面（`Origin: null`），因此需要允许跨源访问。
 * 真正的访问控制由「仅监听回环地址 + {@link AppTokenFilter} 的 token 校验」承担，
 * 此处不做凭据放行（`allowCredentials=false`）。
 */
@Configuration
public class ApiCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("X-App-Token", "Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
