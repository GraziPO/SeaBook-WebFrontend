package seabook.frontend.dto.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Uma linha de GET /api/configuracoes/detalhes. key é o nome gravado no banco
 * (ex.: "loan.period-days"); type é BOOLEAN ou INTEGER.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SettingDetail(String key, String label, String type, Object value, Object defaultValue,
                            Integer min, Integer max, LocalDateTime updatedAt, String updatedBy) {

    public boolean bool() {
        return Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value));
    }

    public boolean defaultBool() {
        return Boolean.TRUE.equals(defaultValue) || "true".equals(String.valueOf(defaultValue));
    }
}
