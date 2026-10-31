package fit.iuh.engolearn.payment.infra.vnpay;
import fit.iuh.engolearn.payment.service.VnpayGateway;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
@Component @RequiredArgsConstructor
public class VnpayHttpGateway implements VnpayGateway {
    private final VnpaySettings settings;
    private final ObjectMapper mapper;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public Map<String,String> post(Map<String,String> request) {
        try {
            var response=client.send(HttpRequest.newBuilder(URI.create(settings.getApiUrl())).timeout(settings.getTimeout())
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
                    .build(),HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200 || response.body().length()>65536) throw new IllegalStateException("VNPay transport response rejected");
            var body=mapper.readTree(response.body());
            if (!body.isObject()) throw new IllegalArgumentException("VNPay response object required");
            Map<String,String> result=new LinkedHashMap<>();
            for (String key:body.propertyNames()) {
                var value=body.get(key);
                if (value.isNull()) result.put(key,"");
                else if (value.isString() || value.isNumber()) result.put(key,value.isString()?value.asString():value.toString());
                else throw new IllegalArgumentException("VNPay scalar response required");
            }
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();throw new IllegalStateException("VNPay request interrupted");
        } catch (java.io.IOException failure) {throw new IllegalStateException("VNPay transport unavailable");}
    }
}
