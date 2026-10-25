package com.nocteon.nocteon_api.auth.config;

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
import org.springframework.web.cors.CorsConfigurationSource;

import com.nocteon.nocteon_api.auth.oauth2.CookieOAuth2AuthorizationRequestRepository;
import com.nocteon.nocteon_api.auth.oauth2.CustomOAuth2UserService;
import com.nocteon.nocteon_api.auth.oauth2.OAuth2FailureHandler;
import com.nocteon.nocteon_api.auth.oauth2.OAuth2SuccessHandler;
import com.nocteon.nocteon_api.auth.security.JwtAuthFilter;
import com.nocteon.nocteon_api.auth.security.RestAuthenticationEntryPoint;
import com.nocteon.nocteon_api.common.filter.RateLimitFilter;
import com.nocteon.nocteon_api.common.filter.UserActivityTrackingFilter;
import com.nocteon.nocteon_api.common.ratelimit.RateLimiterService;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthFilter jwtAuthFilter;
        private final CustomOAuth2UserService customOAuth2UserService;
        private final OAuth2SuccessHandler oAuth2SuccessHandler;
        private final OAuth2FailureHandler oAuth2FailureHandler;
        private final CorsConfigurationSource corsConfigurationSource;
        private final RateLimiterService rateLimiterService;
        private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
        private final UserActivityTrackingFilter userActivityTrackingFilter;
        private final CookieOAuth2AuthorizationRequestRepository cookieOAuth2AuthorizationRequestRepository;
        private final com.nocteon.nocteon_api.common.util.ClientIpResolver clientIpResolver;

        /**
         * Read-only endpoints a storefront visitor may call without a token.
         *
         * <p>Named and package-visible rather than inline in the filter chain because this list has
         * to agree with what the controllers actually map, and nothing in Java enforces that. Three
         * of the entries were wrong at one point — {@code /api/roast-profiles} against a controller
         * mapping {@code /api/roast-levels}, a missing journal, and the roast-level catch-all —
         * and each one is a 401 for the storefront that only shows up in a browser.
         *
         * <p>{@link com.nocteon.nocteon_api.auth.AuthorizationConfigTest} reads this array and
         * asserts that every public controller mapping is covered, so a rename on either side now
         * fails the build instead of production.
         */
        public static final String[] PUBLIC_READ_PATHS = {
                        "/api/categories",
                        "/api/categories/**",
                        "/api/origins",
                        "/api/origins/**",
                        "/api/farms",
                        "/api/farms/**",
                        "/api/roast-levels",
                        "/api/roast-levels/**",
                        "/api/processing-methods",
                        "/api/processing-methods/**",
                        "/api/coffee-varieties",
                        "/api/coffee-varieties/**",
                        "/api/tasting-notes",
                        "/api/tasting-notes/**",
                        "/api/brewing-methods",
                        "/api/brewing-methods/**",
                        "/api/pairings",
                        "/api/pairings/**",
                        // Public journal. JournalController maps /journal and /journal/{slug}, and
                        // neither was listed here, so the storefront's blog returned 401.
                        "/api/journal",
                        "/api/journal/**",
                        "/api/products",
                        "/api/products/**",
                        "/api/settings" };

        @Bean
        public RateLimitFilter rateLimitFilter() {
                return new RateLimitFilter(rateLimiterService, clientIpResolver);
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                                .csrf(AbstractHttpConfigurer::disable)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint(restAuthenticationEntryPoint))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/api/auth/**",
                                                                "/oauth2/**",
                                                                "/login/oauth2/**",
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, PUBLIC_READ_PATHS)
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/orders/payment/webhook")
                                                .permitAll()
                                                // The coffee assistant, public by design: there is no
                                                // authenticated principal in that flow, and it holds no
                                                // tool that can reach a cart, an order or any other
                                                // account data. Permitted for every method because the
                                                // widget posts a turn, then reads and clears history.
                                                // /api/dashboard/** stays authenticated below.
                                                .requestMatchers(
                                                                "/api/ai/chat",
                                                                "/api/ai/chat/**")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/newsletter/subscribe")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/contact")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/newsletter/unsubscribe")
                                                .permitAll()
                                                .requestMatchers("/api/dashboard/**").authenticated()
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .authorizationEndpoint(authorization -> authorization
                                                                .authorizationRequestRepository(
                                                                                cookieOAuth2AuthorizationRequestRepository))
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(customOAuth2UserService))
                                                .successHandler(oAuth2SuccessHandler)
                                                .failureHandler(oAuth2FailureHandler))

                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterAfter(rateLimitFilter(), JwtAuthFilter.class)
                                .addFilterAfter(userActivityTrackingFilter, RateLimitFilter.class);
                return http.build();
        }
}