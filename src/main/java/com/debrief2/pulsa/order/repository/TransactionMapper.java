package com.debrief2.pulsa.order.repository;

import com.debrief2.pulsa.order.payload.dto.TransactionDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface TransactionMapper {

  @Select("SELECT * FROM transaction WHERE id = #{id}")
  TransactionDTO getById(long id);

  @Select("SELECT * FROM transaction WHERE userId = #{userId} ORDER BY createdAt DESC LIMIT 10")
  List<TransactionDTO> getTenRecentByUserId(long userId);

  @Update("UPDATE transaction SET userId=#{userId}, methodId=#{methodId}, phoneNumber=#{phoneNumber}, catalogId=#{catalogId}, voucherId=#{voucherId}, statusId=#{statusId}, updatedAt=NOW() where id=#{id}")
  void update(TransactionDTO transactionDTO);
}
