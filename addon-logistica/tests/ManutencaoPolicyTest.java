import br.com.telemassas.logistica.rateio.ManutencaoPolicy;
public class ManutencaoPolicyTest {
    static int checks=0;
    static void deny(Runnable r) { checks++;try{r.run();throw new AssertionError("Operação deveria ser bloqueada");}catch(IllegalArgumentException|IllegalStateException|SecurityException expected){} }
    public static void main(String[] args) {
        String v="versao";
        ManutencaoPolicy.validar("S",v,v,false,false,false,"N",false); checks++;
        ManutencaoPolicy.validar("S",v,v,true,true,true,null,false); checks++;
        deny(()->ManutencaoPolicy.validar("N",v,v,true,true,true,null,false));
        deny(()->ManutencaoPolicy.validar("S",v,"antiga",false,false,false,"N",false));
        deny(()->ManutencaoPolicy.validar("S",v,v,true,false,true,null,false));
        deny(()->ManutencaoPolicy.validar("S",v,v,true,true,false,null,false));
        deny(()->ManutencaoPolicy.validar("S",v,v,false,false,false,"S",false));
        deny(()->ManutencaoPolicy.validar("S",v,v,false,false,false,null,false));
        deny(()->ManutencaoPolicy.validar("S",v,v,false,false,false,"N",true));
        deny(()->ManutencaoPolicy.validar("S",v,v,true,true,true,"S",false));
        deny(()->ManutencaoPolicy.validar("S",v,v,true,true,true,null,true));
        for(String bad:new String[]{null,"","0","-1","1.5","1e3","' OR 1=1","1000000000000000"})deny(()->ManutencaoPolicy.identificador(bad));
        if(ManutencaoPolicy.identificador("30020").intValue()!=30020)throw new AssertionError(); checks++;
        deny(()->ManutencaoPolicy.motivo("curto"));deny(()->ManutencaoPolicy.motivo(null));
        StringBuilder longReason=new StringBuilder();for(int i=0;i<501;i++)longReason.append('x');deny(()->ManutencaoPolicy.motivo(longReason.toString()));
        if(!ManutencaoPolicy.motivo("  Saneamento documentado  ").equals("Saneamento documentado"))throw new AssertionError(); checks++;
        System.out.println("PASS: "+checks+" verificações das guardas reais de manutenção.");
    }
}
