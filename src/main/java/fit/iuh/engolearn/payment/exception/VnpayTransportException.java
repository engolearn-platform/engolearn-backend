package fit.iuh.engolearn.payment.exception;
import fit.iuh.engolearn.payment.model.enums.ProviderTransportFailure;
import lombok.Getter;
@Getter
public class VnpayTransportException extends RuntimeException {
    private final ProviderTransportFailure failure;
    private final Integer httpStatus;
    private final String causeType;
    public VnpayTransportException(ProviderTransportFailure failure,Integer httpStatus,String causeType) {
        super("VNPay transport failure: "+failure.name());
        this.failure=failure;this.httpStatus=httpStatus;this.causeType=causeType;
    }
}
