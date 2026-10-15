package com.aiasti.tiger.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 本地服务访问校验（BIZ-013）。
 *
 * <p>服务只监听回环地址；除健康检查外，请求需携带 {@value #TOKEN_HEADER} 且与配置一致，
 * 否则返回 401。token 由 Electron 主进程生成并注入（打包期），开发期使用配置默认值。
 * 配置为空时校验关闭（仅用于明确的无鉴权开发场景）。
 */
@Component
public class AppTokenFilter extends OncePerRequestFilter {

    static final String TOKEN_HEADER = "X-App-Token";
    private static final String HEALTH_PATH = "/api/health";

    private final String expectedToken;

    public AppTokenFilter(@Value("${app.security.token:}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expectedToken.isBlank() || HEALTH_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String actual = request.getHeader(TOKEN_HEADER);
        if (!expectedToken.equals(actual)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing app token");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
