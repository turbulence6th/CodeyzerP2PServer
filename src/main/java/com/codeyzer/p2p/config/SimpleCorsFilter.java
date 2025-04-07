package com.codeyzer.p2p.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SimpleCorsFilter implements Filter {

    // CORS için izin verilen origins
    private static final Set<String> ALLOWED_ORIGINS = new HashSet<>(Arrays.asList(
            "http://localhost:3000",
            "http://localhost:8080"
    ));

    // CORS için izin verilen HTTP metodları
    private static final String ALLOWED_METHODS = "POST, GET, OPTIONS, DELETE, PUT";

    // CORS için izin verilen HTTP başlıkları
    private static final String ALLOWED_HEADERS = "Authorization, Content-Type, Accept, X-Requested-With";

    // CORS için izin verilen Header'ların istemciye gönderilmesi
    private static final String EXPOSED_HEADERS = "Content-Disposition, Content-Length";

    // CORS için ön uçak (preflight) isteklerinin önbelleğe alınacağı saniye
    private static final String MAX_AGE = "3600";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;

        String origin = request.getHeader("Origin");

        // Origin kontrolü ile daha güvenli CORS yapılandırması
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            // Hiçbir origin eşleşmediyse, varsayılan olarak localhost'a izin ver
            response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
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
