package com.debrief2.pulsa.order.model;

import com.debrief2.pulsa.order.model.enums.VoucherType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Voucher {
    private long id;
    private String name;
    private long deduction;
    private long maxDeduction;
    @JsonIgnore
    private VoucherType voucherTypeName;
    @JsonIgnore
    private long finalPrice;
    @JsonIgnore
    private long value;
    @JsonIgnore
    private boolean active;
}