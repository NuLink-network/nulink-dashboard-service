package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.ValidPersonalStakingAmount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface ValidPersonalStakingAmountRepository extends JpaRepository<ValidPersonalStakingAmount, Long>, JpaSpecificationExecutor {

    @Query(value = "SELECT\n" +
            "	vpsa.id,\n" +
            "	vpsa.epoch,\n" +
            "	vpsa.staking_amount,\n" +
            "	vpsa.user_address,\n" +
            "	vpsa.token_id,\n" +
            "	vpsa.tx_hash,\n" +
            "	vpsa.create_time,\n" +
            "	vpsa.last_update_time \n" +
            "FROM\n" +
            "	valid_personal_staking_amount vpsa,\n" +
            "	(\n" +
            "	SELECT\n" +
            "		max( id ) id \n" +
            "	FROM\n" +
            "		valid_personal_staking_amount v \n" +
            "	WHERE\n" +
            "		v.token_id = :tokenId \n" +
            "		AND ( v.epoch + 0 ) <= :epoch \n" +
            "	GROUP BY\n" +
            "		v.user_address \n" +
            "	) t \n" +
            "WHERE\n" +
            "	vpsa.token_id = :tokenId \n" +
            "	AND ( vpsa.epoch + 0 ) < :epoch \n" +
            "	AND vpsa.id = t.id", nativeQuery = true)
    List<ValidPersonalStakingAmount> findAllByTokenIdAndEpochLessThanEqual(@Param("tokenId") String tokenId, @Param("epoch") Integer epoch);

    @Query(value = "SELECT\n" +
            "	vpsa.id,\n" +
            "	vpsa.epoch,\n" +
            "	vpsa.staking_amount,\n" +
            "	vpsa.user_address,\n" +
            "	vpsa.token_id,\n" +
            "	vpsa.tx_hash,\n" +
            "	vpsa.create_time,\n" +
            "	vpsa.last_update_time \n" +
            "FROM\n" +
            "	valid_personal_staking_amount vpsa,\n" +
            "	(\n" +
            "	SELECT\n" +
            "		max( id ) id \n" +
            "	FROM\n" +
            "		valid_personal_staking_amount v \n" +
            "	WHERE\n" +
            "		v.user_address = :userAddress \n" +
            "		AND ( v.epoch + 0 ) <= :epoch \n" +
            "	GROUP BY\n" +
            "		v.token_id \n" +
            "	) t \n" +
            "WHERE\n" +
            "	vpsa.user_address = :userAddress \n" +
            "	AND ( vpsa.epoch + 0 ) <= :epoch \n" +
            "	AND vpsa.id = t.id order by vpsa.token_id", nativeQuery = true)
    List<ValidPersonalStakingAmount> findAllByUserAddressAndEpochLessThanEqual(@Param("userAddress") String userAddress, @Param("epoch") Integer epoch);

    List<ValidPersonalStakingAmount> findAllByTokenId(String tokenId);

    ValidPersonalStakingAmount findFirstByTokenIdAndUserAddressAndCreateTimeBeforeOrderByCreateTimeDesc(String tokenId, String userAddress, Timestamp createTime);

    List<ValidPersonalStakingAmount> findAllByUserAddress(String userAddress);

    ValidPersonalStakingAmount findByTxHash(String txHash);

    List<ValidPersonalStakingAmount> findAllByUserAddressAndTxHashNot(String userAddress, String txHash);

    List<ValidPersonalStakingAmount> findAllByUserAddressAndCreateTimeAfter(String userAddress, Timestamp createTime);

}
