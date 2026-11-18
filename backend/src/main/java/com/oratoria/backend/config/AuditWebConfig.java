package com.oratoria.backend.config;

import com.oratoria.backend.admin.audit.AdminAuditInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuditWebConfig implements WebMvcConfigurer {
    private final AdminAuditInterceptor adminAudit;

    public AuditWebConfig(AdminAuditInterceptor adminAudit) { this.adminAudit = adminAudit; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAudit).addPathPatterns("/api/admin/**");
    }
}
