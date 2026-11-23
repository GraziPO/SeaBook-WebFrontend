package seabook.frontend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Une os formatos de resposta que as rotas de login do backend podem devolver:
 *  - termos pendentes:   {termsRequired, termsToken, termsVersion, privacyVersion}
 *  - MFA pendente:       {mfaRequired, mfaToken, email, expiresInSeconds}
 *  - troca de senha:     {passwordChallengeRequired, passwordChangeToken, expiresInSeconds}
 *  - login concluído:    {token, username, role_code, mfaTrustToken?, mfaTrustExpiresInSeconds?}
 *    (mfaTrustToken só vem quando o código do MFA acabou de ser confirmado: vai para o cookie que
 *     lembra o navegador por 30 min)
 * ignoreUnknown: se o backend passar a mandar um campo novo, o login não quebra.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthResult {
    private Boolean termsRequired;
    private String termsToken;
    private String termsVersion;
    private String privacyVersion;

    private Boolean mfaRequired;
    private String mfaToken;
    private String email;
    private Long expiresInSeconds;

    private Boolean passwordChallengeRequired;
    private String passwordChangeToken;

    private String token;
    private String username;
    private String role_code;

    private String mfaTrustToken;
    private Long mfaTrustExpiresInSeconds;

    public boolean isTermsChallenge() {
        return token == null && termsToken != null;
    }

    public boolean isMfaChallenge() {
        return token == null && mfaToken != null;
    }

    public boolean isPasswordChangeChallenge() {
        return token == null && passwordChangeToken != null;
    }

    public boolean isFullyAuthenticated() {
        return token != null;
    }
}
