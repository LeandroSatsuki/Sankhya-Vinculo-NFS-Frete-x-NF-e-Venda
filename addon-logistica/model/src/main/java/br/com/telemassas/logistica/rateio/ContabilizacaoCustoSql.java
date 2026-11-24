package br.com.telemassas.logistica.rateio;

/** Fixed SQL fragments only. No user-supplied identifiers or inferred TGFCAB status column. */
final class ContabilizacaoCustoSql {
    private ContabilizacaoCustoSql() { }

    static String estado(String custo, String cabecalho) {
        // Callers are internal, fixed expressions. Never accept arbitrary SQL from a request.
        if (!("V.TFV_NUNOTA_FRETE".equals(custo) || "C.CUSTO".equals(custo)) || !"F.NUNOTA".equals(cabecalho))
            throw new IllegalArgumentException("Expressão de custo não autorizada.");
        return "CASE WHEN EXISTS (SELECT 1 FROM TCBINT CI WHERE CI.NUNICO=" + custo +
            " AND CI.ORIGEM IN ('E','L')) THEN 'S' WHEN " + cabecalho + " IS NULL THEN NULL ELSE 'N' END";
    }
}
