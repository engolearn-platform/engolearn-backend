package fit.iuh.engolearn.payment.infra.scheduling;
import fit.iuh.engolearn.payment.infra.vnpay.VnpaySettings;
import fit.iuh.engolearn.payment.service.VnpayService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class VnpayWorkScheduler {
    private final VnpayService vnpay;
    private final VnpaySettings settings;
    @Scheduled(fixedDelayString="${engolearn.payment.vnpay.poll-delay-ms:5000}")
    public void process() {if (settings.isEnabled() && settings.isWorkerEnabled()) vnpay.process();}
}
