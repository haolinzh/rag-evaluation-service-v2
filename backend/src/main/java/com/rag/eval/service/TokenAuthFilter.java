package com.rag.eval.service;

import com.rag.eval.model.AppUser;
import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.repository.UserRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    private final TokenStore tokenStore;
    private final UserRepo userRepo;

    public TokenAuthFilter(TokenStore tokenStore, UserRepo userRepo) {
        this.tokenStore = tokenStore;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long userId = tokenStore.resolve(header.substring(7));
            if (userId != null) {
                userRepo.findById(userId).ifPresent(user -> {
                    if (user.isEnabled()) {
                        AuthenticatedUser principal = AuthService.toAuthenticatedUser(user);
                        List<SimpleGrantedAuthority> authorities = principal.permissions().stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                        UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                });
            }
        }
        chain.doFilter(request, response);
    }
}
