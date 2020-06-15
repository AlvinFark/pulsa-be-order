package com.debrief2.pulsa.order.service;

import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.Provider;
import com.debrief2.pulsa.order.model.PulsaCatalog;
import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import com.debrief2.pulsa.order.payload.response.AllPulsaCatalogResponse;

public interface ProviderService {
  void reloadCatalog();

  void reloadPrefix();

  Provider getProviderByPrefix(String prefix);
  AllPulsaCatalogResponse getAllCatalog(String phone) throws ServiceException;
  PulsaCatalogDTO getCatalogDTObyId(long id);
  Provider getProviderById(long id);
  PulsaCatalog catalogDTOToCatalogAdapter(PulsaCatalogDTO catalogDTO);
}
