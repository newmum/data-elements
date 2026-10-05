package com.linewell.dataelement.model.common;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
public class BaseBusinessException extends RuntimeException {
    private static final long serialVersionUID = -6415101345737331631L;

    @Builder.Default
    private String errorCode = "-1";
    private String errorMessage;

    public BaseBusinessException(String errorMessage) {
        super(new Throwable(errorMessage));
        this.errorMessage = errorMessage;
    }

    public BaseBusinessException(String errorCode, String errorMessage) {
        super(new Throwable(errorMessage));
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public BaseBusinessException(String errorMessage, Throwable cause) {
        super(cause);
        this.errorMessage = errorMessage;
    }

    public BaseBusinessException(String errorCode, String errorMessage, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

}
