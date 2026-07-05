package sg.edu.nus.iss.wellness.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads bearer tokens and creates authenticated Spring Security principals.
 *
 * <p>When a token cannot be used, the reason (missing / expired / malformed / user-invalid) is
 * recorded as a request attribute so {@link LoginRedirectAuthenticationEntryPoint} can decide
 * whether to redirect the client to the login page or return a 401.</p>
 *
 * @author SA62 Team
 * @author JustinChua97
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            request.setAttribute(AuthFailureReason.ATTRIBUTE, AuthFailureReason.MISSING);
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        String email;
        try {
            email = jwtService.extractSubject(token);
        } catch (ExpiredJwtException ex) {
            request.setAttribute(AuthFailureReason.ATTRIBUTE, AuthFailureReason.EXPIRED);
            filterChain.doFilter(request, response);
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            request.setAttribute(AuthFailureReason.ATTRIBUTE, AuthFailureReason.MALFORMED);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails user = userDetailsService.loadUserByUsername(email);
                if (jwtService.isValid(token, user.getUsername())) {
                    // Authorities come from the freshly loaded user, whose Role enum yields
                    // ROLE_USER. Reloading (rather than trusting the token's role claim) keeps
                    // the database the single source of truth for authorization.
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    // Parsed and unexpired, but the subject did not match: treat as malformed.
                    request.setAttribute(AuthFailureReason.ATTRIBUTE, AuthFailureReason.MALFORMED);
                }
            }
        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();
            request.setAttribute(AuthFailureReason.ATTRIBUTE, AuthFailureReason.EXPIRED);
        } catch (Exception ex) {
            // Token was structurally valid but the account could not be resolved (unknown/disabled).
            SecurityContextHolder.clearContext();
            request.setAttribute(AuthFailureReason.ATTRIBUTE, AuthFailureReason.USER_INVALID);
        }
        filterChain.doFilter(request, response);
    }
}
