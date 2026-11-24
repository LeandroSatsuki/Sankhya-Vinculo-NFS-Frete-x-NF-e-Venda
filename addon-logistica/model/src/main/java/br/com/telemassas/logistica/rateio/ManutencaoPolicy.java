package br.com.telemassas.logistica.rateio;

import java.math.BigDecimal;

/** Pure business guards, shared by the service and executable regression tests. */
public final class ManutencaoPolicy {
    private ManutencaoPolicy() { }
    public static BigDecimal identificador(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,14}"))
            throw new IllegalArgumentException("Informe um número inteiro positivo, com até 15 dígitos.");
        return new BigDecimal(value);
    }
    public static String motivo(String value) {
        String result = value == null ? "" : value.trim();
        if (result.length() < 10 || result.length() > 500)
            throw new IllegalArgumentException("Informe um motivo entre 10 e 500 caracteres.");
        return result;
    }
    public static void validar(String ativo, String versaoAtual, String versaoInformada,
                               boolean orfao, boolean saneamento, boolean podeSanear,
                               String contabilizado, boolean baixado) {
        if (!"S".equals(ativo)) throw new IllegalStateException("Vínculo já inativo. Atualize a consulta.");
        if (versaoAtual == null || !versaoAtual.equals(versaoInformada))
            throw new IllegalStateException("O vínculo ou documento foi alterado. Consulte novamente.");
        if ("S".equals(contabilizado))
            throw new IllegalStateException("O custo possui contabilização relacionada: inativação bloqueada.");
        if (baixado) throw new IllegalStateException("O custo possui financeiro baixado: inativação bloqueada.");
        if (orfao) {
            if (!saneamento || !podeSanear)
                throw new SecurityException("Documento ausente: exige confirmação e permissão de saneamento.");
        } else {
            if (!"N".equals(contabilizado))
                throw new IllegalStateException("Contabilização confirmada ou não comprovada: inativação bloqueada.");
        }
    }
}
