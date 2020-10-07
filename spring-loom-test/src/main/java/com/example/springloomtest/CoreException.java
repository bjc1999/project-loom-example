package com.example.springloomtest;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

// Based on Builder's pattern, do not use Lombok's @Data nor @??ArgsConstructor
// Making this class abstract as we are not supposed to initialize this DdaBaseException by itself.
@NoArgsConstructor
public abstract class CoreException extends Exception {
    @Getter @Setter
    protected String requestId;
    @Getter
    protected String errorSourceOrigin = String.valueOf(CoreSingletons.currentApi);
    @Getter
    protected String errorSourceImmediate = String.valueOf(CoreSingletons.currentApi);
    @Getter
    protected String errorCode;
    @Getter
    protected String errorCategory;
    @Getter
    protected int errorHttpCode;
    // We uses message details to give more insights of what causes the error.
    // usually we put the 3rd party REST response here
    @Getter
    protected String errorMessageDetails;
    @Getter
    protected String errorMessageFriendly;
    @Getter
    protected String errorStackTrace;

    // Probably too complex to be initialized by fresh grads,
    // Hijacking Builder's design pattern to initialize this
    public static CoreException createException(CoreErrorEnumInterface errorEnum, String messageDetails, Throwable t) {
        CoreException result = null;
        if (CoreErrorCategory.EXPECTED.equals(errorEnum.getErrorCategory())) {
            result = new CoreExpectedException(errorEnum, messageDetails, t);
        } else {
            result = new CoreUnexpectedException(errorEnum, messageDetails, t);
        }
        List<StackTraceElement> stackTraceElementsOld = new LinkedList<>(Arrays.asList(result.getStackTrace()));
        //Remove the 1st stack, as this function is another stack of the stack trace
        stackTraceElementsOld.remove(0);
        result.setStackTrace(stackTraceElementsOld.toArray(stackTraceElementsOld.toArray(new StackTraceElement[stackTraceElementsOld.size()])));
        result.errorStackTrace = ExceptionUtils.getStackTrace(result);

        if (((messageDetails == null) || (messageDetails.isEmpty())) && (t != null)) {
            messageDetails = t.getMessage();
            result.errorStackTrace = ExceptionUtils.getStackTrace(t);
            if (t instanceof HttpClientErrorException) {
                messageDetails = ((HttpClientErrorException) t).getResponseBodyAsString();
            } else if (t instanceof HttpServerErrorException) {
                messageDetails = ((HttpServerErrorException) t).getResponseBodyAsString();
            }
        }
        result.errorMessageDetails = messageDetails;
        return result;
    }

    public static CoreException createException(CoreErrorEnumInterface errorEnum, Throwable t) {
        return createException(errorEnum, null, t);
    }

    public static CoreException rebuildCoreExceptionFromRestResponse(String errorResponse, ApiEnum targetSystem) throws Exception {
        CoreException exception = null;
        CoreException fakeException = null;

        if (errorResponse.contains("\"errorCategory\":\"EXPECTED\"")) {
            fakeException = CoreSingletons.objectMapper.readValue(errorResponse, CoreExpectedException.class);
            fakeException.setTargetSystem(targetSystem);
            exception = new CoreExpectedException(fakeException.getErrorCode(),
                    fakeException.getErrorCategory(),
                    fakeException.getErrorHttpCode(),
                    fakeException.getErrorSourceOrigin(),
                    fakeException.getErrorSourceImmediate(),
                    fakeException.getErrorMessageFriendly(),
                    fakeException.getErrorMessageDetails(),
                    fakeException.getErrorStackTrace());
        } else if (errorResponse.contains("\"errorCategory\":\"UNEXPECTED\"")) {
            fakeException = CoreSingletons.objectMapper.readValue(errorResponse, CoreUnexpectedException.class);
            fakeException.setTargetSystem(targetSystem);
            exception = new CoreUnexpectedException(fakeException.getErrorCode(),
                    fakeException.getErrorCategory(),
                    fakeException.getErrorHttpCode(),
                    fakeException.getErrorSourceOrigin(),
                    fakeException.getErrorSourceImmediate(),
                    fakeException.getErrorMessageFriendly(),
                    fakeException.getErrorMessageDetails(),
                    fakeException.getErrorStackTrace());
        }


        return exception;
    }

    protected CoreException(CoreErrorEnumInterface errorEnum, String messageDetails, Throwable t) {
        super(String.valueOf(errorEnum) + "\n" + errorEnum.getErrorMessageFriendly() + (((messageDetails != null) && (!messageDetails.isEmpty())) ? "\n" + messageDetails : ""), t);
        this.errorCode = errorEnum.name();
        this.errorCategory = errorEnum.getErrorCategory().name();
        this.errorHttpCode = errorEnum.getErrorHttpCode();
        this.errorMessageFriendly = errorEnum.getErrorMessageFriendly();
        this.errorMessageDetails = messageDetails;
    }

    protected CoreException(String errorCode,
                            String errorCategory,
                            int errorHttpCode,
                            String errorSourceOrigin,
                            String errorSourceImmediate,
                            String errorMessageFriendly,
                            String errorMessageDetails,
                            String errorStackTrace) {
        super(errorCode + "\n" + errorMessageFriendly + (((errorMessageDetails != null) && (!errorMessageDetails.isEmpty())) ? "\n" + errorMessageDetails : ""));
        this.errorCode = errorCode;
        this.errorCategory = errorCategory;
        this.errorHttpCode = errorHttpCode;
        this.errorSourceOrigin = errorSourceOrigin;
        this.errorSourceImmediate = errorSourceImmediate;
        this.errorMessageFriendly = errorMessageFriendly;
        this.errorMessageDetails = errorMessageDetails;
        this.errorStackTrace = errorStackTrace;
    }

    public CoreException setCurrentSystem(ApiEnum currentSystem) {
        this.errorSourceImmediate = String.valueOf(currentSystem);
        return this;
    }

    public CoreException setTargetSystem(ApiEnum targetSystem) {
        this.errorSourceOrigin = String.valueOf(targetSystem);
        return this;
    }
}
