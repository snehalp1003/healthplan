package com.me.healthplan;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.me.healthplan.service.HealthPlanAuthorizationService;
import com.me.healthplan.utility.AuthorizationFilter;

@SpringBootApplication
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.me.healthplan*" )
public class HealthplanApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthplanApplication.class, args);
    }
    
    @Bean
    public FilterRegistrationBean<AuthorizationFilter> jwtFilterRegistrationBean(HealthPlanAuthorizationService authorizationService){
        FilterRegistrationBean<AuthorizationFilter> registrationBean = new FilterRegistrationBean<>();

        ObjectMapper mapper = new ObjectMapper();
        registrationBean.setFilter(new AuthorizationFilter(mapper, authorizationService));
        registrationBean.setUrlPatterns(Arrays.asList("/plan","/plan/*"));

        return registrationBean;
    }
}
