package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.ContractOffset;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.util.List;

@Repository
public interface ContractOffsetRepository extends PagingAndSortingRepository<ContractOffset, Long> {

    ContractOffset findByContractAddress(String contractAddress);

    List<ContractOffset> findAll();

    @Query("select min(c.blockOffset) from ContractOffset c")
    BigInteger findMinBlockOffset();

}
