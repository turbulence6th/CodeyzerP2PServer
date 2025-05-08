package com.codeyzer.p2p.config;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CorsFilter implements Filter {
    
    private final CorsProperties corsProperties;
    
    // CORS için izin verilen HTTP metodları
    private static final String ALLOWED_METHODS = "POST, GET, OPTIONS, DELETE, PUT";

    // CORS için izin verilen HTTP başlıkları
    private static final String ALLOWED_HEADERS = "Authorization, Content-Type, Accept, X-Requested-With, x-owner-token";

    // CORS için izin verilen Header'ların istemciye gönderilmesi
    private static final String EXPOSED_HEADERS = "Content-Disposition, Content-Length";

    // CORS için ön uçak (preflight) isteklerinin önbelleğe alınacağı saniye
    private static final String MAX_AGE = "3600";
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;

        String origin = request.getHeader("Origin");
        var allowedOrigins = corsProperties.getCors().getAllowedOrigins();

        // Origin kontrolü ile daha güvenli CORS yapılandırması
        if (origin != null && allowedOrigins.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        } else if (!allowedOrigins.isEmpty()) {
            // Hiçbir origin eşleşmediyse, varsayılan olarak listedeki ilk origin'e izin ver
            response.setHeader("Access-Control-Allow-Origin", allowedOrigins.get(0));
        }

        response.setHeader("Access-Control-Allow-Methods", ALLOWED_METHODS);
        response.setHeader("Access-Control-Max-Age", MAX_AGE);
        response.setHeader("Access-Control-Allow-Headers", ALLOWED_HEADERS);
        response.setHeader("Access-Control-Expose-Headers", EXPOSED_HEADERS);
        response.setHeader("Access-Control-Allow-Credentials", "true");

        // OPTIONS metodu (preflight istekleri) için hızlı yanıt
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            chain.doFilter(req, res);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // Başlatma işlemi gerekmediğinde boş bırakılabilir
    }

    @Override
    public void destroy() {
        // Yok etme işlemi gerekmediğinde boş bırakılabilir
    }
} 