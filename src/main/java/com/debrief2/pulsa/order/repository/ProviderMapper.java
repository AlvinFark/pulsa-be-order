package com.debrief2.pulsa.order.repository;

import com.debrief2.pulsa.order.model.Provider;
import com.debrief2.pulsa.order.payload.dto.ProviderPrefixDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface ProviderMapper {

  @Select("SELECT * FROM provider")
  List<Provider> getAll();

  @Select("SELECT * FROM provider_prefix WHERE deletedAt IS NULL")
  List<ProviderPrefixDTO> getAllPrefix();
}
