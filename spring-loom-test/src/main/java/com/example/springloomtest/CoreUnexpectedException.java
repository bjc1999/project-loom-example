package com.example.springloomtest;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class CoreUnexpectedException extends CoreException {

    //Use 'public static CoreException createException(MpBaseErrorEnumInterface errorEnum, Throwable t)' to initialize this Exception
    protected CoreUnexpectedException(CoreErrorEnumInterface errorEnum, String messageDetails, Throwable t) {
        super(errorEnum, messageDetails, t);
    }

    protected CoreUnexpectedException(String errorCode,
                                      String errorCategory,
                                      int errorHttpCode,
                                      String errorSourceOrigin,
                                      String errorSourceImmediate,
                                      String errorMessageFriendly,
                                      String errorMessageDetails,
                                      String errorStackTrace) {
        super(errorCode,
                errorCategory,
                errorHttpCode,
                errorSourceOrigin,
                errorSourceImmediate,
                errorMessageFriendly,
                errorMessageDetails,
                errorStackTrace);
    }

    @Override
    public String toString() {
        String result = "";
        if (this.getMessage() != null) {
            result = "CoreUnexpectedException: " + this.getMessage();
        }
        else {
            result = this.getErrorStackTrace();
        }
        return result;
    }
}
