package com.aiasti.tiger.domain.model;

/** 曲目状态；契约值与前端 `TrackStatus` 一致（小写）。 */
public enum TrackStatus {

    SAVED("saved"),
    EDITED("edited"),
    FAILED("failed");

    private final String contractValue;

    TrackStatus(String contractValue) {
        this.contractValue = contractValue;
    }

    public String contractValue() {
        return contractValue;
    }
}
