package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.ContractOffset;
import com.nulink.livingratio.repository.ContractOffsetRepository;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Date;

@Service
public class ContractOffsetService {

    private final ContractOffsetRepository contractOffsetRepository;

    public ContractOffsetService(ContractOffsetRepository contractOffsetRepository) {
        this.contractOffsetRepository = contractOffsetRepository;
    }

    public ContractOffset findByContractAddress(String contractAddress){
        return contractOffsetRepository.findByContractAddress(contractAddress);
    }

    public void update(ContractOffset contractOffset){
        contractOffsetRepository.save(contractOffset);
    }

    public BigInteger findMinBlockOffset(){
        return contractOffsetRepository.findMinBlockOffset();
    }

    public void updateOffset(ContractOffset contractOffset, Integer delayBlocks, BigInteger offset){
        String contractAddress = "Delay" + delayBlocks + "_" + "BLOCK_CONTRACT_FLAG";

        if (null == contractOffset) {
            contractOffset = new ContractOffset();
            contractOffset.setContractAddress(contractAddress);
            contractOffset.setContractName("ALL_CONTRACTS");
            contractOffset.setRecordedAt(new Timestamp(new Date().getTime()));
        }
        contractOffset.setBlockOffset(offset);
        contractOffsetRepository.save(contractOffset);
    }
}
