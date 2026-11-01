package fit.iuh.engolearn.payment.service;
import java.util.Map;
public interface VnpayGateway {
    Map<String,String> post(Map<String,String> request);
}
