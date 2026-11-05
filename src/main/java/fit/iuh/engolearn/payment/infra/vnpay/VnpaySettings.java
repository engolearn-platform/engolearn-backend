package fit.iuh.engolearn.payment.infra.vnpay;

import java.net.URI;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component @ConfigurationProperties("engolearn.payment.vnpay") @Getter @Setter
public class VnpaySettings {
    private boolean enabled;
    private boolean workerEnabled=true;
    private String tmnCode="";
    private String hashSecret="";
    private String returnUrl="";
    private String serverIp="";
    private String payUrl="https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String apiUrl="https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private Duration timeout=Duration.ofSeconds(10);
    private Duration lease=Duration.ofSeconds(60);
    private Duration queryInterval=Duration.ofMinutes(6);
    private int batchSize=10;
    public void validate() {
        if (!enabled) return;
        if (!tmnCode.matches("[A-Za-z0-9]{8}") || hashSecret.isBlank() || !serverIp.matches("[0-9a-fA-F:.]{2,45}") ||
                returnUrl.isBlank() || timeout.isNegative() || timeout.isZero() || lease.compareTo(timeout.multipliedBy(2))<=0 ||
                queryInterval.compareTo(Duration.ofMinutes(5))<=0 || batchSize<1 || batchSize>100)
            throw new IllegalStateException("VNPay requires complete merchant settings and valid timing limits");
        for (String value:new String[]{payUrl,apiUrl,returnUrl}) {
            URI uri=URI.create(value);
            boolean local="localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null ||
                    !("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme())))
                throw new IllegalStateException("VNPay URLs require HTTPS (HTTP allowed only on loopback)");
        }
    }
    @jakarta.annotation.PostConstruct void checkConfiguration() {validate();}
}
