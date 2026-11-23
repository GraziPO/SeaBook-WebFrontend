package seabook.frontend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Pattern;

/**
 * Redireciona para /login qualquer página que exija sessão autenticada.
 * Exceção: a tela Localização é pública para CONSULTA. Só os GET dela passam sem login
 * (a página, o modal "Detalhes" e os endereços antigos que redirecionam para ela);
 * cadastrar, editar e excluir são POST e continuam exigindo sessão.
 */
public class LoginRequiredInterceptor implements HandlerInterceptor {

    private static final Pattern PUBLIC_READ = Pattern.compile(
            "^/acervo/departamentos(/[^/]+)?/?$"
            + "|^/acervo/localizacoes(/[^/]+(/detalhes)?)?/?$");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("jwt") != null) {
            return true;
        }
        if (isPublicRead(request)) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }

    /** GET de uma página da Localização (consulta pública). */
    static boolean isPublicRead(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return PUBLIC_READ.matcher(path).matches();
    }
}
