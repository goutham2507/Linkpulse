package com.linkpulse.shortener_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.linkpulse.shortener_service.security.JwtAuthFilter;
import com.linkpulse.shortener_service.security.JwtService;
import com.linkpulse.shortener_service.security.RestAccessDeniedHandler;
import com.linkpulse.shortener_service.security.RestAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   RestAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/auth/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/{code}").permitAll()
                        // must come before anyRequest(): the first matching rule wins
                        // hasRole("ADMIN") checks authority "ROLE_ADMIN", which JwtAuthFilter grants
                        .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(new RestAccessDeniedHandler()))
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
