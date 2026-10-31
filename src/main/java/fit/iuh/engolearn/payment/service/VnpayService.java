package fit.iuh.engolearn.payment.service;
import fit.iuh.engolearn.payment.dto.response.*;
import java.util.*;
public interface VnpayService {
    VnpayIpnResponse ipn(Map<String,List<String>> parameters);
    VnpayReturnResponse returned(Map<String,List<String>> parameters);
    void process();
}
