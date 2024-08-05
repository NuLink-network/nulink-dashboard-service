package com.nulink.livingratio.constant;

import lombok.Getter;

@Getter
public enum NodePoolEventEnum {

    STAKING("STAKING"),

    UN_STAKING("UN_STAKING"),

    CLAIM("CLAIM"),

    CLAIM_REWARD("CLAIM_REWARD");

    public final String name;

    NodePoolEventEnum(String name) {
        this.name = name;
    }

}
