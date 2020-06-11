package com.debrief2.pulsa.order.repository;

import com.debrief2.pulsa.order.payload.dto.TransactionDTO;
import org.apache.ibatis.annotations.*;
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

  @Update("UPDATE transaction SET statusId=#{expiredId} where id=#{id} AND statusId=#{waitingId} AND DATE_ADD(createdAt, INTERVAL #{lifetime} HOUR) < NOW()")
  void refreshStatusById(long id, int lifetime, long expiredId, long waitingId);

  @Select("SELECT transaction.* FROM transaction INNER JOIN transaction_status ON transaction.statusId = transaction_status.id WHERE userId = #{userId} AND transaction_status.typeId = #{statusTypeId} ORDER BY transaction.createdAt DESC LIMIT #{offset},10")
  List<TransactionDTO> getAllByUserIdAndStatusTypeIdAndOffset(long userId, long statusTypeId, long offset);

  @Select("SELECT * FROM transaction WHERE userId=#{userId} AND phoneNumber=#{phoneNumber} AND catalogId=#{catalogId} AND methodId=#{walletId} AND voucherId=0 AND statusId=#{waitingId} AND DATE_ADD(createdAt, INTERVAL 30 SECOND) > NOW()")
  TransactionDTO checkExistWithin30second(long userId, String phoneNumber, long catalogId, long walletId, long waitingId);

  @Insert("INSERT INTO transaction(userId,methodId,phoneNumber,catalogId,statusId) VALUES(#{userId},#{methodId},#{phoneNumber},#{catalogId},#{statusId})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insert(TransactionDTO transactionDTO);
}
