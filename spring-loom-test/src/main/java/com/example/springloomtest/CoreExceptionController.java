package com.example.springloomtest;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ControllerAdvice
@Log4j2
public class CoreExceptionController extends ResponseEntityExceptionHandler {
    private static final String TRACE_ID = "traceId";
    private static final String UNKNOWN = "Unknown";
    private static final String ERROR = "error";
    private static final String ERROR_TYPE = "errorType";
    private static final String STACK_TRACE = "stackTrace";

//    @Autowired
//    private Rollbar rollbar;

    @ExceptionHandler(CoreExpectedException.class)
    public ResponseEntity<CoreErrorResponse> handleExpectedException(HttpServletRequest request, String requestId, CoreExpectedException e, Throwable errorInterceptorError) {
        //External Api's Expected Error, pass along
        CoreErrorResponse errorResponse = new CoreErrorResponse();
        errorResponse.setRequestId(requestId);
        errorResponse.setErrorSourceOrigin(e.getErrorSourceOrigin());
        errorResponse.setErrorSourceImmediate(CoreSingletons.currentApi.toString());
        errorResponse.setErrorCategory(CoreErrorCategory.EXPECTED.name());
        errorResponse.setErrorCode(e.getErrorCode());
        errorResponse.setErrorHttpCode(e.getErrorHttpCode());
        errorResponse.setErrorMessageFriendly(e.getErrorMessageFriendly());
        errorResponse.setErrorMessageDetails(e.getErrorMessageDetails());
        errorResponse.setErrorStackTrace(e.getErrorStackTrace());
        if (errorInterceptorError != null) {
            errorResponse.setErrorInterceptorStackTrace(ExceptionUtils.getStackTrace(errorInterceptorError));
        }
        return ResponseEntity.status(errorResponse.getErrorHttpCode()).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CoreErrorResponse> handleUnexpectedException(HttpServletRequest request, String requestId, Throwable t, Throwable errorInterceptorError) {

        CoreErrorResponse errorResponse = new CoreErrorResponse();
        errorResponse.setRequestId(requestId);
        if (t instanceof CoreUnexpectedException) {
            //External Api's Unexpected Error, pass along
            CoreUnexpectedException e2 = (CoreUnexpectedException) t;
            errorResponse.setErrorCategory(e2.getErrorCategory());
            errorResponse.setErrorSourceOrigin(e2.getErrorSourceOrigin());
            errorResponse.setErrorSourceImmediate(CoreSingletons.currentApi.toString());
            errorResponse.setErrorCode(e2.getErrorCode());
            errorResponse.setErrorHttpCode(e2.getErrorHttpCode());
            errorResponse.setErrorMessageFriendly(e2.getErrorMessageFriendly());
            errorResponse.setErrorMessageDetails(e2.getErrorMessageDetails());
            errorResponse.setErrorStackTrace(e2.getErrorStackTrace());
        } else {
            //Internal Unexpected Error
            errorResponse.setErrorCategory(CoreError.INTERNAL_SERVER_ERROR.getErrorCategory().name());
            errorResponse.setErrorSourceOrigin(CoreSingletons.currentApi.toString());
            errorResponse.setErrorSourceImmediate(CoreSingletons.currentApi.toString());
            errorResponse.setErrorCode(CoreError.INTERNAL_SERVER_ERROR.name());
            errorResponse.setErrorHttpCode(CoreError.INTERNAL_SERVER_ERROR.getErrorHttpCode());
            errorResponse.setErrorMessageFriendly(t.getMessage());
            errorResponse.setErrorStackTrace(ExceptionUtils.getStackTrace(t));
        }
        if (errorInterceptorError != null) {
            errorResponse.setErrorInterceptorStackTrace(ExceptionUtils.getStackTrace(errorInterceptorError));
        }

        return ResponseEntity.status(errorResponse.getErrorHttpCode()).body(errorResponse);
    }

    private String getRequestVariable(HttpServletRequest request, String variableName) {
        // Try the query string first: ?userId={userId}
        String queryUserId = request.getParameter(variableName);
        if (queryUserId != null && !queryUserId.isEmpty()) {
            return Optional.of(queryUserId).orElse(UNKNOWN);
        }

        // Try a path variable next: .../{userId}
        Map pathVariables = (Map) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        queryUserId = String.valueOf(pathVariables.get(variableName));
        if (queryUserId != null && !queryUserId.isEmpty()) {
            return Optional.of(queryUserId).orElse(UNKNOWN);
        }

        return Optional.ofNullable(request.getAttribute(variableName)).map(Object::toString).orElse(UNKNOWN);
    }

    private void rollbarError(HttpServletRequest request, Exception e, String traceId) {
        //rollbar.error(e, getRollbarMap(request, traceId));
    }

    private HashMap<String, Object> getRollbarMap(HttpServletRequest request, String traceId) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("url", request.getRequestURL());
        map.put("traceId", traceId);
        return map;
    }

}
