package seabook.frontend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginRequiredInterceptor())
                .excludePathPatterns(
                        "/",   // decide no controller: logado vai para a página inicial do perfil, senão para o login
                        // login em etapas
                        "/login", "/mfa", "/mfa/verify", "/mfa/resend", "/logout",
                        "/termos", "/termos/aceitar", "/termos-de-uso", "/politica-de-privacidade",
                        "/primeiro-acesso",
                        // recuperação de senha
                        "/esqueci-senha", "/redefinir-senha",
                        // consulta pública ao acervo (consulta sem login); os endereços antigos redirecionam
                        "/acervo/consulta", "/acervo/consulta/**", "/consulta", "/consulta/**",
                        // arquivos estáticos
                        "/css/**", "/js/**", "/vendor/**", "/images/**", "/favicon.ico",
                        // health check do Railway (sem isso, ele recebe um redirecionamento para o login)
                        "/actuator/health", "/actuator/health/**",
                        "/error"
                );
    }
}
