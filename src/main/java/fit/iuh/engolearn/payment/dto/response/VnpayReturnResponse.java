package fit.iuh.engolearn.payment.dto.response;
public record VnpayReturnResponse(boolean validSignature, String purchaseReference, String message) {}
