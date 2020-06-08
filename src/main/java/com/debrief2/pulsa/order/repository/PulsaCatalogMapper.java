package com.debrief2.pulsa.order.repository;

import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface PulsaCatalogMapper {

  @Select("SELECT * FROM pulsa_catalog")
  List<PulsaCatalogDTO> getAll();
}
