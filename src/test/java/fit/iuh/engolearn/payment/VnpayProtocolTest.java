package fit.iuh.engolearn.payment;

import fit.iuh.engolearn.payment.utils.VnpayProtocol;
import fit.iuh.engolearn.payment.infra.vnpay.VnpaySettings;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VnpayProtocolTest {
    @Test void sha512MatchesRfc4231AndRejectsTampering(){
        String hash=VnpayProtocol.hash("\u000b".repeat(20),"Hi There");
        assertThat(hash).isEqualTo("87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cde"+
                "daa833b7d6b8a702038b274eaea3f4e4be9d914eeb61f1702e696c203a126854");
        assertThat(VnpayProtocol.matches(hash,hash.toUpperCase(Locale.ROOT))).isTrue();
        assertThat(VnpayProtocol.matches(hash,"00".repeat(64))).isFalse();
        assertThat(VnpayProtocol.matches(hash,"invalid")).isFalse();
    }
    @Test void canonicalQueryEncodesOnceSortsAndExcludesSignatureFields(){
        Map<String,String> fields=new LinkedHashMap<>();
        fields.put("vnp_ReturnUrl","https://example.test/return?x=1&y=2");fields.put("vnp_OrderInfo","Premium + plan");
        fields.put("vnp_Amount","10000");fields.put("vnp_BankCode","");fields.put("vnp_SecureHash","ignored");
        fields.put("vnp_SecureHashType","SHA512");
        assertThat(VnpayProtocol.query(fields)).isEqualTo("vnp_Amount=10000&vnp_OrderInfo=Premium+%2B+plan&vnp_ReturnUrl=https%3A%2F%2Fexample.test%2Freturn%3Fx%3D1%26y%3D2");
        fields.put("vnp_SecureHash",VnpayProtocol.hash("fixture-secret",VnpayProtocol.query(fields)));
        assertThat(VnpayProtocol.verifyQuery("fixture-secret",fields)).isTrue();
        fields.put("vnp_Amount","20000");assertThat(VnpayProtocol.verifyQuery("fixture-secret",fields)).isFalse();
    }
    @Test void moneyBoundsDatesAndEnabledConfigurationFailClosed(){
        assertThat(VnpayProtocol.amount(100000)).isEqualTo("10000000");assertThat(VnpayProtocol.vnd("10000000")).isEqualTo(100000);
        assertThatThrownBy(()->VnpayProtocol.vnd("101")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->VnpayProtocol.amount(10_000_000_000L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->VnpayProtocol.instant("20270230000000")).isInstanceOf(java.time.DateTimeException.class);
        assertThat(VnpayProtocol.date(java.time.Instant.parse("2027-01-31T03:00:00Z"))).isEqualTo("20270131100000");
        VnpaySettings settings=new VnpaySettings();settings.validate();settings.setEnabled(true);
        assertThatThrownBy(settings::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test void gatewayUsesJsonPostAndParsesNumericProviderAmount()throws Exception{
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var body=new java.util.concurrent.atomic.AtomicReference<String>();
        var method=new java.util.concurrent.atomic.AtomicReference<String>();
        var type=new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/transaction",exchange->{
            body.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            method.set(exchange.getRequestMethod());type.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] data="{\"vnp_Amount\":10000000,\"vnp_ResponseCode\":\"00\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,data.length);
            exchange.getResponseBody().write(data);exchange.close();
        });
        server.start();
        try{
            VnpaySettings settings=new VnpaySettings();settings.setApiUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/transaction");
            var gateway=new fit.iuh.engolearn.payment.infra.vnpay.VnpayHttpGateway(settings,new tools.jackson.databind.ObjectMapper());
            var result=gateway.post(Map.of("vnp_Command","querydr","vnp_RequestId","request1"));
            assertThat(method.get()).isEqualTo("POST");assertThat(type.get()).isEqualTo("application/json");
            assertThat(body.get()).contains("\"vnp_Command\":\"querydr\"","\"vnp_RequestId\":\"request1\"");
            assertThat(result.get("vnp_Amount")).isEqualTo("10000000");assertThat(result.get("vnp_ResponseCode")).isEqualTo("00");
        }finally{server.stop(0);}
    }
}
