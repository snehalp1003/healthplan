/**
 * 
 */
package com.me.healthplan.utility;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.me.healthplan.service.HealthPlanAuthorizationService;

/**
 * @author Snehal Patel
 */

@Component
public class AuthorizationFilter extends OncePerRequestFilter {

    private final ObjectMapper mapper;
    private final HealthPlanAuthorizationService authorizationService;

    public AuthorizationFilter(ObjectMapper mapper, HealthPlanAuthorizationService authorizationService) {
        this.mapper = mapper;
        this.authorizationService = authorizationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException {
        final String authorizationHeader = httpServletRequest.getHeader("Authorization");

        if(authorizationHeader == null || authorizationHeader.isEmpty()){
            AuthorizationErrorResponse authorizationErrorResponse = new AuthorizationErrorResponse(
                    "Empty Token!",
                    HttpStatus.UNAUTHORIZED.getReasonPhrase()
            );

            httpServletResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
            httpServletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);

            mapper.writeValue(httpServletResponse.getWriter(), authorizationErrorResponse);
            return;
        }

        boolean isValid;
        try {
            String token = authorizationHeader.substring(7);
            isValid = authorizationService.validateToken(token);
        } catch (Exception e) {
            System.out.println(e);
            isValid = false;
        }

        if ( !isValid ) {
            AuthorizationErrorResponse authorizationErrorResponse = new AuthorizationErrorResponse(
                    "Invalid Token !",
                    HttpStatus.UNAUTHORIZED.getReasonPhrase()
            );

            httpServletResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
            httpServletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);

            mapper.writeValue(httpServletResponse.getWriter(), authorizationErrorResponse);
            return;
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

}
