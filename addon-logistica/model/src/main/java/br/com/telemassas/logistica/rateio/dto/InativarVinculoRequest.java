package br.com.telemassas.logistica.rateio.dto;

public class InativarVinculoRequest {
    private String custo, nota, motivo, versao;
    private Boolean saneamentoOrfao;
    public String getCusto() { return custo; }
    public void setCusto(String v) { custo=v; }
    public String getNota() { return nota; }
    public void setNota(String v) { nota=v; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String v) { motivo=v; }
    public String getVersao() { return versao; }
    public void setVersao(String v) { versao=v; }
    public Boolean getSaneamentoOrfao() { return saneamentoOrfao; }
    public void setSaneamentoOrfao(Boolean v) { saneamentoOrfao=v; }
}
