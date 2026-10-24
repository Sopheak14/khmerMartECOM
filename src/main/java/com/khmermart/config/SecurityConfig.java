package com.khmermart.config;

import com.khmermart.security.CustomAccessDeniedHandler;
import com.khmermart.security.JwtAuthEntrypoint;
import com.khmermart.security.JwtAuthFilter;
import com.khmermart.security.RateLimitFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;
  private final JwtAuthEntrypoint authEntryPoint;
  private final CustomAccessDeniedHandler accessDeniedHandler;
  private final RateLimitFilter rateLimitFilter;
  private final AppProperty appProperties;

  public SecurityConfig(
      JwtAuthFilter jwtAuthFilter,
      JwtAuthEntrypoint authEntryPoint,
      CustomAccessDeniedHandler accessDeniedHandler,
      RateLimitFilter rateLimitFilter,
      AppProperty appProperties) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.authEntryPoint = authEntryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
    this.rateLimitFilter = rateLimitFilter;
    this.appProperties = appProperties;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(authEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Allow internal forwards and error dispatches
                    .dispatcherTypeMatchers(
                        jakarta.servlet.DispatcherType.FORWARD,
                        jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()

                    // Public / Infrastructure
                    .requestMatchers("/uploads/**")
                    .permitAll()
                    .requestMatchers("/api/health")
                    .permitAll()
                    .requestMatchers(
                        "/api/docs",
                        "/api/docs/**",
                        "/api/v3/api-docs",
                        "/api/v3/api-docs/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/api/swagger-ui/**",
                        "/api/swagger-ui.html",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()

                    // Public Auth endpoints
                    .requestMatchers(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/auth/forgot-password",
                        "/api/auth/reset-password",
                        "/api/auth/verify-email")
                    .permitAll()

                    // Public Catalog, Search, Settings & Home
                    .requestMatchers(HttpMethod.GET, "/api/settings/public")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/home")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/categories/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/products/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/search/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/shipping/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/track/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/push/vapid-key", "/api/push/vapid-public-key")
                    .permitAll()
                    .requestMatchers("/api/dev/**")
                    .permitAll()

                    // Cart endpoints (allow guest cart)
                    .requestMatchers("/api/cart/**")
                    .permitAll()

                    // KHQR Payment SSE Stream
                    .requestMatchers("/api/payments/*/stream")
                    .permitAll()

                    // Admin Only (👑)
                    .requestMatchers("/api/admin/users/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/admin/payments/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/admin/reports/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/admin/settings/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/admin/audit-logs/**")
                    .hasRole("ADMIN")

                    // Staff or Admin (🛠)
                    .requestMatchers("/api/admin/**")
                    .hasAnyRole("STAFF", "ADMIN")

                    // All other API endpoints require authentication
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(appProperties.frontendUrl()));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
    configuration.setExposedHeaders(List.of("Content-Disposition", "X-Request-Id"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
