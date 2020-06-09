package com.debrief2.pulsa.order.model;

import com.debrief2.pulsa.order.model.enums.VoucherType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class Voucher {
    private long id;
    @JsonIgnore
    private VoucherType voucherType;
    private String name;
    @JsonIgnore
    private long discount;
    private long deduction;
    private long maxDeduction;
    @JsonIgnore
    private long value;
}