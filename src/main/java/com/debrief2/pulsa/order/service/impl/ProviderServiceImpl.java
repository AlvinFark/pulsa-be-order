package com.debrief2.pulsa.order.service.impl;

import com.debrief2.pulsa.order.model.Provider;
import com.debrief2.pulsa.order.payload.dto.ProviderPrefixDTO;
import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import com.debrief2.pulsa.order.payload.response.PulsaCatalogResponse;
import com.debrief2.pulsa.order.repository.ProviderMapper;
import com.debrief2.pulsa.order.repository.PulsaCatalogMapper;
import com.debrief2.pulsa.order.service.AsyncAdapter;
import com.debrief2.pulsa.order.service.ProviderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ProviderServiceImpl implements ProviderService {

  @Autowired
  ProviderMapper providerMapper;
  @Autowired
  PulsaCatalogMapper pulsaCatalogMapper;
  @Autowired
  AsyncAdapter asyncAdapter;

  private static HashMap<Long, Provider> mapProviderById = new HashMap<>();
  private static HashMap<String, Long> mapProviderIdByPrefix = new HashMap<>();
  private static HashMap<Long, PulsaCatalogDTO> mapCatalogDTOById = new HashMap<>();
  private static HashMap<Long, ArrayList<PulsaCatalogResponse>> mapListCatalogResponseByProviderId = new HashMap<>();

  @Override
  public void reloadCatalog(){
    //get from db
    List<PulsaCatalogDTO> pulsaCatalogDTOS = pulsaCatalogMapper.getAll();
    //put it in hash map
    mapCatalogDTOById = new HashMap<>();
    mapListCatalogResponseByProviderId = new HashMap<>();
    for (PulsaCatalogDTO pulsaCatalogDTO:pulsaCatalogDTOS) {
      mapCatalogDTOById.put(pulsaCatalogDTO.getId(),pulsaCatalogDTO);
      if (pulsaCatalogDTO.getDeletedAt()==null) {
        mapListCatalogResponseByProviderId.computeIfAbsent(pulsaCatalogDTO.getProviderId(), k -> new ArrayList<>()).add(new PulsaCatalogResponse(pulsaCatalogDTO));
      }
    }
  }

  public void reloadProvider(){
    //get from db
    List<Provider> providers = providerMapper.getAll();
    //convert provider to map
    mapProviderById = new HashMap<>();
    for (Provider provider:providers){
      mapProviderById.put(provider.getId(),provider);
    }
  }

  @Override
  public void reloadPrefix(){
    //get from db
    List<ProviderPrefixDTO> providerPrefixDTOS = providerMapper.getAllPrefix();
    mapProviderIdByPrefix = new HashMap<>();
    for (ProviderPrefixDTO providerPrefixDTO:providerPrefixDTOS){
      mapProviderIdByPrefix.put(providerPrefixDTO.getPrefix(),providerPrefixDTO.getProviderId());
    }
  }

  @Override
  public void checkAllCache(){
    if (mapProviderById.isEmpty()||mapProviderIdByPrefix.isEmpty()||mapCatalogDTOById.isEmpty()
        ||mapListCatalogResponseByProviderId.isEmpty()){
      reloadProvider();
      CompletableFuture<Void> asyncReloadCatalog = asyncAdapter.reloadCatalog();
      CompletableFuture<Void> asyncReloadPrefix = asyncAdapter.reloadPrefix();
      CompletableFuture.allOf(asyncReloadCatalog,asyncReloadPrefix);
    }
  }

  @Override
  public Provider getProviderByPrefix(String prefix) {
    checkAllCache();
    try {
      long providerId = mapProviderIdByPrefix.get(prefix);
      return mapProviderById.get(providerId);
    } catch (NullPointerException e){
      return null;
    }
  }

  @Override
  public List<PulsaCatalogResponse> getCatalogResponseByProviderId(long providerId) {
    checkAllCache();
    try {
      return mapListCatalogResponseByProviderId.get(providerId);
    } catch (NullPointerException e){
      return null;
    }
  }

  @Override
  public PulsaCatalogDTO getCatalogDTObyId(long id) {
    checkAllCache();
    try {
      return mapCatalogDTOById.get(id);
    } catch (NullPointerException e){
      return null;
    }
  }

  @Override
  public Provider getProviderById(long id) {
    checkAllCache();
    try {
      return mapProviderById.get(id);
    } catch (NullPointerException e){
      return null;
    }
  }
}
