package fit.iuh.engolearn.payment.infra.web;
import fit.iuh.engolearn.payment.dto.response.*;
import fit.iuh.engolearn.payment.service.VnpayService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
@Controller @RequiredArgsConstructor
@RequestMapping("/api/payment/vnpay")
public class VnpayCallbackController {
    private final VnpayService vnpay;
    @GetMapping(value="/ipn",produces="application/json") @ResponseBody
    public VnpayIpnResponse ipn(@RequestParam MultiValueMap<String,String> parameters,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return vnpay.ipn(parameters);
    }
    @GetMapping(value="/return",produces="application/json") @ResponseBody
    public VnpayReturnResponse returned(@RequestParam MultiValueMap<String,String> parameters,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return vnpay.returned(parameters);
    }
}
