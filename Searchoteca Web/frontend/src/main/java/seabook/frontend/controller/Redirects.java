package seabook.frontend.controller;

/**
 * Volta para a tela de onde o formulário veio (campo hidden "returnTo", preenchido pelo layout).
 * Só aceita caminhos internos: "/acervo/departamentos?page=2" sim; "https://..." ou "//site" não.
 * Assim ninguém usa o campo para mandar o usuário para outro site.
 */
final class Redirects {
    private Redirects() {}

    static String back(String returnTo, String fallback) {
        boolean safe = returnTo != null
                && returnTo.startsWith("/")
                && !returnTo.startsWith("//")
                && !returnTo.contains("\\")
                && returnTo.chars().noneMatch(c -> c == '\r' || c == '\n');
        return "redirect:" + (safe ? returnTo : fallback);
    }
}
