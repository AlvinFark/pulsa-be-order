package com.debrief2.pulsa.order.service;

import com.debrief2.pulsa.order.model.Provider;
import com.debrief2.pulsa.order.model.PulsaCatalog;
import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import com.debrief2.pulsa.order.payload.response.PulsaCatalogResponse;

import java.util.List;

public interface ProviderService {
  void checkAllCache();
  void reloadPrefix();
  void reloadCatalog();
  Provider getProviderByPrefix(String prefix);
  List<PulsaCatalogResponse> getCatalogResponseByProviderId(long providerId);
  PulsaCatalogDTO getCatalogDTObyId(long id);
  Provider getProviderById(long id);
  PulsaCatalog catalogDTOToCatalogAdapter(PulsaCatalogDTO catalogDTO);
}
