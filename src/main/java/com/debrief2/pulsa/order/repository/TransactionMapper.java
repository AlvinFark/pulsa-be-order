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

  @Update("UPDATE transaction SET statusId=#{expiredId} where userId=#{userId} AND statusId=#{waitingId} AND DATE_ADD(createdAt, INTERVAL #{lifetime} HOUR) < NOW()")
  void refreshStatus(long userId, int lifetime, long expiredId, long waitingId);

  @Select("SELECT transaction.* FROM transaction INNER JOIN transaction_status ON transaction.statusId = transaction_status.id WHERE userId = #{userId} AND transaction_status.typeId = #{statusTypeId} ORDER BY transaction.createdAt DESC LIMIT #{offset},10")
  List<TransactionDTO> getAllByUserIdAndStatusTypeIdAndOffset(long userId, long statusTypeId, long offset);
}
