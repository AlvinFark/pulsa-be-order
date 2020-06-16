package com.debrief2.pulsa.order.service.impl;

import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.Provider;
import com.debrief2.pulsa.order.model.PulsaCatalog;
import com.debrief2.pulsa.order.payload.dto.ProviderPrefixDTO;
import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import com.debrief2.pulsa.order.payload.response.AllPulsaCatalogResponse;
import com.debrief2.pulsa.order.payload.response.PulsaCatalogResponse;
import com.debrief2.pulsa.order.repository.ProviderMapper;
import com.debrief2.pulsa.order.repository.PulsaCatalogMapper;
import com.debrief2.pulsa.order.service.AsyncAdapter;
import com.debrief2.pulsa.order.service.ProviderService;
import com.debrief2.pulsa.order.utils.ResponseMessage;
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
    //save into map
    mapProviderIdByPrefix = new HashMap<>();
    for (ProviderPrefixDTO providerPrefixDTO:providerPrefixDTOS){
      mapProviderIdByPrefix.put(providerPrefixDTO.getPrefix(),providerPrefixDTO.getProviderId());
    }
  }

  private void checkAllCache(){
    //check whether any of the map empty, do reload if true
    if (mapProviderById.isEmpty()||mapProviderIdByPrefix.isEmpty()||mapCatalogDTOById.isEmpty() ||mapListCatalogResponseByProviderId.isEmpty()){
      reloadProvider();
      CompletableFuture<Void> asyncReloadCatalog = asyncAdapter.reloadCatalog();
      CompletableFuture<Void> asyncReloadPrefix = asyncAdapter.reloadPrefix();
      CompletableFuture.allOf(asyncReloadCatalog,asyncReloadPrefix);
    }
  }

  @Override
  public Provider getProviderByPrefix(String prefix) {
    //check first, then get from memory
    checkAllCache();
    try {
      long providerId = mapProviderIdByPrefix.get(prefix);
      return mapProviderById.get(providerId);
    } catch (NullPointerException e){
      return null;
    }
  }

  public List<PulsaCatalogResponse> getCatalogResponseByProviderId(long providerId) {
    //check first, then get from memory
    checkAllCache();
    try {
      return mapListCatalogResponseByProviderId.get(providerId);
    } catch (NullPointerException e){
      return null;
    }
  }

  @Override
  public PulsaCatalogDTO getCatalogDTObyId(long id) {
    //check first, then get from memory
    checkAllCache();
    try {
      return mapCatalogDTOById.get(id);
    } catch (NullPointerException e){
      return null;
    }
  }

  @Override
  public Provider getProviderById(long id) {
    //check first, then get from memory
    checkAllCache();
    return mapProviderById.get(id);
  }

  @Override
  public PulsaCatalog catalogDTOToCatalogAdapter(PulsaCatalogDTO catalogDTO){
    return PulsaCatalog.builder()
        .id(catalogDTO.getId())
        .provider(getProviderById(catalogDTO.getProviderId()))
        .value(catalogDTO.getValue())
        .price(catalogDTO.getPrice())
        .deletedAt(catalogDTO.getDeletedAt())
        .build();
  }

  @Override
  public AllPulsaCatalogResponse getAllCatalog(String phone) throws ServiceException {
    //validate format
    if (phone.length()!=5||phone.charAt(0)!='0'){
      throw new ServiceException(ResponseMessage.getAllCatalog400);
    }

    //validate if phone number prefix exist
    Provider provider = getProviderByPrefix(phone.substring(1));
    if (provider==null||provider.getDeletedAt()!=null){
      throw new ServiceException(ResponseMessage.getAllCatalog404);
    }

    return new AllPulsaCatalogResponse(provider,getCatalogResponseByProviderId(provider.getId()));
  }
}
