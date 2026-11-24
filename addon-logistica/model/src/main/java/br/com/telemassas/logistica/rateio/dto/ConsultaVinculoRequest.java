package br.com.telemassas.logistica.rateio.dto;

public class ConsultaVinculoRequest {
    private String tipo, numero, empresa;
    private Integer pagina;
    public String getTipo() { return tipo; }
    public void setTipo(String v) { tipo=v; }
    public String getNumero() { return numero; }
    public void setNumero(String v) { numero=v; }
    public String getEmpresa() { return empresa; }
    public void setEmpresa(String v) { empresa=v; }
    public Integer getPagina() { return pagina; }
    public void setPagina(Integer v) { pagina=v; }
}
