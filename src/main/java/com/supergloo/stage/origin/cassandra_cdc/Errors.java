package com.supergloo.stage.origin.cassandra_cdc;

import com.streamsets.pipeline.api.ErrorCode;
import com.streamsets.pipeline.api.GenerateResourceBundle;

@GenerateResourceBundle
public enum Errors implements ErrorCode {

    SAMPLE_00("A configuration is invalid because: {}"),
    SAMPLE_01("Specific reason writing record failed: {}"),
    ;
    private final String msg;

    Errors(String msg) {
        this.msg = msg;
    }

    /** {@inheritDoc} */
    @Override
    public String getCode() {
        return name();
    }

    /** {@inheritDoc} */
    @Override
    public String getMessage() {
        return msg;
    }
}