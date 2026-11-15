package seabook.frontend.dto;

public record MfaVerifyRequest(String mfaToken, String code) {}
