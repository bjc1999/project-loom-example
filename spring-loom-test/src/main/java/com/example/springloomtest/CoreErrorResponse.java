package com.example.springloomtest;

import lombok.Data;

@Data
public class CoreErrorResponse {
    private String requestId;
    private String errorSourceOrigin;
    private String errorSourceImmediate;
    private String errorCategory;
    private String errorCode;
    private int errorHttpCode;
    private String errorMessageDetails;
    private String errorMessageFriendly;
    private String errorStackTrace;
    private String errorInterceptorStackTrace;
}
