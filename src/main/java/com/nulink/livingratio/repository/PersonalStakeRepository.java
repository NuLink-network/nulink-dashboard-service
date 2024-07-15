package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.event.StakingEvent;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface PersonalStakeRepository extends PagingAndSortingRepository<StakingEvent, Long>, JpaSpecificationExecutor {

    StakingEvent findByTxHash(String txHash);

    @Query(value = "SELECT\n" +
                    "	s.* \n" +
                    "FROM\n" +
                    "	personal_stake s,\n" +
                    "	( SELECT max( id ) id, USER FROM personal_stake WHERE create_time <= :epochStartTime GROUP BY USER ) t \n" +
                    "WHERE\n" +
                    "	s.USER = t.USER \n" +
                    "	AND s.id = t.id \n" +
                    "	AND s.`event` = 'stake'", nativeQuery = true)
    List<StakingEvent> findValidStakeByEpoch(@Param("epochStartTime") Timestamp epochStartTime);

    @Query(value = "SELECT\n" +
                    "	s.* \n" +
                    "FROM\n" +
                    "	personal_stake s,\n" +
                    "	( SELECT max( create_time ) create_time, USER FROM personal_stake GROUP BY USER ) t \n" +
                    "WHERE\n" +
                    "	s.USER = t.USER \n" +
                    "	AND s.create_time = t.create_time ", nativeQuery = true)
    List<StakingEvent> findLatest();

    StakingEvent findFirstByUserAndEventOrderByCreateTimeDesc(String user, String event);

    StakingEvent findFirstByUserAndEventAndCreateTimeBeforeOrderByCreateTimeDesc(String user, String event, Timestamp createTIme);

    List<StakingEvent> findAllByUserAndEventAndCreateTimeBetween(String user, String event, Timestamp startTime, Timestamp endTime);

    List<StakingEvent> findAllByUser(String user);

    List<StakingEvent> findAll();

    List<StakingEvent> findAllByEpochOrderByCreateTime(String epoch);
}
