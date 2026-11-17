package seabook.frontend.dto.archive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Uma localização na busca da tela Movimentações (GET /api/localizacao/estantes), com os exemplares na estante. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationShelf(String localCode, String localName, String departCode, String departName, long available) {}
