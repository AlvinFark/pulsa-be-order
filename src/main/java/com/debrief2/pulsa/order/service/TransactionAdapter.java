package com.debrief2.pulsa.order.service;

import com.debrief2.pulsa.order.model.Provider;
import com.debrief2.pulsa.order.model.PulsaCatalog;
import com.debrief2.pulsa.order.model.Transaction;
import com.debrief2.pulsa.order.model.Voucher;
import com.debrief2.pulsa.order.model.enums.VoucherType;
import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import com.debrief2.pulsa.order.payload.dto.TransactionDTO;
import com.debrief2.pulsa.order.payload.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TransactionAdapter {

  @Autowired
  TransactionService transactionService;
  @Autowired
  ProviderService providerService;

  public Transaction transactionDTOtoTransactionAdapter(TransactionDTO transactionDTO, Voucher voucher){
    if (voucher!=null&&voucher.getVoucherTypeName()==VoucherType.discount){
      voucher.setDeduction(transactionDTO.getDeduction());
    }
    return Transaction.builder()
        .id(transactionDTO.getId())
        .method(transactionService.getPaymentMethodNameById(transactionDTO.getMethodId()))
        .phoneNumber(transactionDTO.getPhoneNumber())
        .catalog(providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(transactionDTO.getCatalogId())))
        .voucher(voucher)
        .status(transactionService.getTransactionStatusNameById(transactionDTO.getStatusId()))
        .createdAt(transactionDTO.getCreatedAt())
        .updatedAt(transactionDTO.getUpdatedAt())
        .build();
  }

  public TransactionOverview transactionDTOtoTransactionOverviewAdapter(TransactionDTO transactionDTO){
    PulsaCatalog catalog = providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(transactionDTO.getCatalogId()));
    return TransactionOverview.builder()
        .id(transactionDTO.getId())
        .phoneNumber(transactionDTO.getPhoneNumber())
        .price(catalog.getPrice())
        .voucher(transactionDTO.getDeduction())
        .status(transactionService.getTransactionStatusNameById(transactionDTO.getStatusId()))
        .createdAt(transactionDTO.getCreatedAt())
        .build();
  }

  public TransactionWithMethodId transactionDTOtoTransactionWithMethodIdAdapter(TransactionDTO transactionDTO){
    return TransactionWithMethodId.builder()
        .id(transactionDTO.getId())
        .methodId(transactionDTO.getMethodId())
        .phoneNumber(transactionDTO.getPhoneNumber())
        .catalog(providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(transactionDTO.getCatalogId())))
        .status(transactionService.getTransactionStatusNameById(transactionDTO.getStatusId()))
        .createdAt(transactionDTO.getCreatedAt())
        .updatedAt(transactionDTO.getUpdatedAt())
        .build();
  }

  public OrderResponse transactionDTOtoOrderResponseAdapter(TransactionDTO transactionDTO){
    PulsaCatalogDTO pulsaCatalogDTO = providerService.getCatalogDTObyId(transactionDTO.getCatalogId());
    PulsaCatalog pulsaCatalog = providerService.catalogDTOToCatalogAdapter(pulsaCatalogDTO);
    return OrderResponse.builder()
        .id(transactionDTO.getId())
        .phoneNumber(transactionDTO.getPhoneNumber())
        .catalog(pulsaCatalog)
        .build();
  }

  public TransactionNoVoucher transactionDTOtoTransactionNoVoucherAdapter(TransactionDTO transactionDTO){
    return TransactionNoVoucher.builder()
        .id(transactionDTO.getId())
        .method(transactionService.getPaymentMethodNameById(transactionDTO.getMethodId()))
        .phoneNumber(transactionDTO.getPhoneNumber())
        .catalog(providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(transactionDTO.getCatalogId())))
        .status(transactionService.getTransactionStatusNameById(transactionDTO.getStatusId()))
        .createdAt(transactionDTO.getCreatedAt())
        .updatedAt(transactionDTO.getUpdatedAt())
        .build();
  }

  public RecentNumberResponse transactionDTOtoRecentNumberResponseAdapter(TransactionDTO transactionDTO){
    Provider provider = providerService.getProviderByPrefix(transactionDTO.getPhoneNumber().substring(1,5));
    return RecentNumberResponse.builder()
        .number(transactionDTO.getPhoneNumber())
        .provider(provider)
        .date(transactionDTO.getCreatedAt())
        .build();
  }
}
