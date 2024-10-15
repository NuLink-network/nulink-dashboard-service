package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.event.SendFeeEvent;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SendFeeEventRepository extends PagingAndSortingRepository<SendFeeEvent, Long>, JpaSpecificationExecutor {

    SendFeeEvent findByTxHash(String txHash);

    List<SendFeeEvent> findAllByUserOrderByCreateTimeDesc(String user);

}
