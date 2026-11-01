package fit.iuh.engolearn.payment.dto.response;
import com.fasterxml.jackson.annotation.JsonProperty;
public record VnpayIpnResponse(@JsonProperty("RspCode") String code, @JsonProperty("Message") String message) {}
