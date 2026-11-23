package seabook.frontend.dto;

/** mfaTrustToken: o que está no cookie do navegador depois do último MFA confirmado (pode ser null). */
public record LoginRequest(String username, String password, String mfaTrustToken) {

    public LoginRequest(String username, String password) {
        this(username, password, null);
    }
}
