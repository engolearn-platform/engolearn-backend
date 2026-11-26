package fit.iuh.engolearn.subscription.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SetProductSaleStatusRequest(@NotNull @Pattern(regexp = "ON_SALE|STOPPED") String saleStatus) {}
