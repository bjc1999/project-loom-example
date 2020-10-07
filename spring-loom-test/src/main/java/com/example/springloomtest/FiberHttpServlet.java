package com.example.springloomtest;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class FiberHttpServlet {

    public static Map<Long, Fiber> fiberHttpServletTracker = Collections.synchronizedMap(new HashMap<>());

    private static long fiberIdNext = 0l;

    private static synchronized long getNextFiberId() {
        return fiberIdNext++;
    }

    //We don't use lambda to target the function we want to call here because lambda functions doesn't support Exception
    public static void serve(final HttpServletRequest servletRequest, final HttpServletResponse servletResponse,
                             Object controllerBean, String endpointName,
                             final String[] parameterNames,
                             final Object[] parameters,
                             FiberHttpServletInterceptor<Method, Map> requestInterceptor,
                             FiberHttpServletInterceptor<Method, Map> responseInterceptor,
                             FiberHttpServletInterceptor<Method, Map> errorInterceptor,
                             boolean timeoutEnabled) throws Exception {
        Class controllerClass = controllerBean.getClass();
        Method targetMethod = getTargetMethod(controllerClass, endpointName, parameters);

        PrintWriter out = servletResponse.getWriter();
        servletResponse.setContentType("application/json");

        long fiberId = getNextFiberId();

        AsyncContext asyncContext = servletRequest.startAsync();
        //Set it never timeout using native AsyncContext, another HttpThread pool executor... !#^$&%@^#%$
        //We use fiber to perform timeout instead
        asyncContext.setTimeout(0);
        Map<String, Object> parameterMap = new LinkedHashMap<>();

        Fiber fiber = Fiber.schedule(new Runnable() {
            public void run() {
                try {
                    Map<String, Object> request = new HashMap<>();
                    for (int count = 0; count < targetMethod.getParameterCount(); count++) {
                        request.put(parameterNames[count], parameters[count]);
                        parameterMap.put(parameterNames[count], parameters[count]);
                    }
                    parameterMap.put("_request", request);
                    try {
                        if (requestInterceptor != null) {
                            requestInterceptor.accept(targetMethod, parameterMap);
                        }
                        Object result = targetMethod.invoke(controllerBean, parameters);
                        try {
                            Field httpStatusField = result.getClass().getDeclaredField("httpStatus");
                            httpStatusField.setAccessible(true);
                            Object httpStatusPreValue = httpStatusField.get(result);
                            if (httpStatusPreValue != null) {
                                Integer httpStatusValue = Integer.valueOf(String.valueOf(httpStatusPreValue));
                                servletResponse.setStatus(httpStatusValue);
                                parameterMap.put("_httpStatus", httpStatusValue);
                            }
                        } catch (Throwable t) {
                            // Field does not exist in the result, continue with 200
                            parameterMap.put("_httpStatus", 200);
                        }
                        parameterMap.put("_response", result);
                        if (responseInterceptor != null) {
                            responseInterceptor.accept(targetMethod, parameterMap);
                        }
                        if ((result instanceof String) ||
                                (result instanceof Boolean) ||
                                (result instanceof Number)) {
                            servletResponse.setContentType("text/html");
                        }
                        if (out != null) {
                            //Timeout response already returned to caller
                            out.print(CoreSingletons.objectMapper.writeValueAsString(result));
                        }
                    } catch (Throwable t) {
                        Throwable t2 = t;
                        if (t2 instanceof InvocationTargetException) {
                            t2 = t2.getCause();
                        }
                        Integer httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
                        if (t2 instanceof CoreExpectedException) {
                            CoreExpectedException expectedException = (CoreExpectedException) t2;
                            httpStatus = expectedException.getErrorHttpCode();

                        } else if (t2 instanceof CoreUnexpectedException) {
                            CoreUnexpectedException unexpectedPresetException = (CoreUnexpectedException) t2;
                            httpStatus = unexpectedPresetException.getErrorHttpCode();
                        }
                        servletResponse.setStatus(httpStatus);
                        parameterMap.put("_httpStatus", httpStatus);
                        Throwable errorInterceptorError = null;
                        if (errorInterceptor != null) {
                            parameterMap.put("_error", t);
                            try {
                                errorInterceptor.accept(targetMethod, parameterMap);
                            } catch (Throwable tInterceptor) {
                                errorInterceptorError = tInterceptor;
                            }
                        }
                        ResponseEntity responseEntity = null;

                        String requestId = (String) parameterMap.get("requestId");
                        if (t2 instanceof CoreExpectedException) {
                            responseEntity = CoreSingletons.baseExceptionController.handleExpectedException(servletRequest, requestId, (CoreExpectedException) t2, errorInterceptorError);
                        } else {
                            responseEntity = CoreSingletons.baseExceptionController.handleUnexpectedException(servletRequest, requestId, t2, errorInterceptorError);
                        }
                        if (out != null) {
                            //Timeout response already returned to caller
                            out.print(CoreSingletons.objectMapper.writeValueAsString(responseEntity.getBody()));
                        }
                    }
                } catch (Throwable t) {
                    // This is for the line:
                    // - out.print(MpSingletons.objectMapper.writeValueAsString(responseEntity.getBody()));
                    // Should never reach here, but if it does, send error to rollbar
                    t.printStackTrace();
                    String requestId = (String) parameterMap.get("requestId");
                    CoreSingletons.baseExceptionController.handleUnexpectedException(servletRequest, requestId, t, null);
                } finally {
                    fiberHttpServletTracker.remove(fiberId);
                    try {
                        asyncContext.complete();
                    } catch (Exception e) {

                    }
                }
            }
        });

        if (timeoutEnabled) {
            final AtomicLong timeoutMillisecond = new AtomicLong();
            timeoutMillisecond.set(10000);
            // DO NOT USE AsyncContext.setTimeout() & AsyncContext.setListener() because they both uses HttpThread
            // also, antipattern... :/
            Fiber.schedule(new Runnable() {
                public void run() {
                    try {
                        if (timeoutMillisecond.get() == 0) {
                            return;
                        }
                        while (Thread.State.NEW.equals(Thread.currentThread().getState())) {
                            Thread.sleep(1);
                        }
                        Date start = new Date();
                        boolean isTimeout = false;
                        while (fiber.isAlive()) {
                            Date now = new Date();
                            if ((now.getTime() - start.getTime()) >= timeoutMillisecond.get()) {
                                isTimeout = true;
                                break;
                            }
                            Thread.sleep(1);
                        }
                        if (isTimeout) {
                            StackTraceElement[] currentFiberStackTrace = null;
                            if (Thread.State.RUNNABLE.equals(Thread.currentThread().getState())) {
                                // Somehow we only able to retrieve the fiber's stack if it is RUNNING, not any other else
                                currentFiberStackTrace = Thread.currentThread().getStackTrace();
                            }
                            CoreException fullException = null;
                            ResponseEntity<CoreErrorResponse> responseEntity = null;
                            TimeoutException timeoutException = null;
                            if (currentFiberStackTrace != null) {
                                timeoutException = new TimeoutException("Execution stack when request timeout");
                                timeoutException.setStackTrace(currentFiberStackTrace);
                            }
                            fullException = CoreException.createException(CoreError.REQUEST_TIMEOUT, "Someone implemented a slow endpoint. Refer to the stack trace on where it stuck - " + getEndpointInfomation(controllerBean, targetMethod), timeoutException);
                            servletResponse.setStatus(fullException.getErrorHttpCode());
                            String requestId = (String) parameterMap.get("requestId");
                            responseEntity = CoreSingletons.baseExceptionController.handleUnexpectedException(
                                    servletRequest,
                                    requestId,
                                    fullException,
                                    null
                            );
                            Throwable errorInterceptorError;
                            if (errorInterceptor != null) {
                                parameterMap.put("_error", new TimeoutException());
                                try {
                                    errorInterceptor.accept(targetMethod, parameterMap);
                                } catch (Throwable t2) {
                                    responseEntity.getBody().setErrorInterceptorStackTrace(ExceptionUtils.getStackTrace(t2));
                                }
                            }

                            out.print(CoreSingletons.objectMapper.writeValueAsString(responseEntity.getBody()));
                            asyncContext.complete();
                        }
                    } catch (Exception e) {
                        // Should never even reach here...
                        e.printStackTrace();
                    }
                }
            });
        }

        fiberHttpServletTracker.put(fiberId, fiber);
    }

    private static String getEndpointInfomation(Object controllerBean, Method targetMethod) {
        Parameter[] methodParameters = targetMethod.getParameters();
        StringBuffer result = new StringBuffer();
        result.append(controllerBean.getClass() + "::");
        result.append(targetMethod.getName() + "(");
        String[] methodParametersString = new String[methodParameters.length];
        for (int countParameter = 0; countParameter < methodParameters.length; countParameter++) {
            methodParametersString[countParameter] = methodParameters[countParameter].getType().getName();
        }
        result.append(String.join(",", methodParametersString) + ")\n");
        return result.toString();
    }

    // Limited by method name & parameter count
    // For performance purposes, this doesn't perform much check on it.
    // Do not confuse yourselves with polymorphism,
    // - write a distinguishable endpoint names if it's 2 different endpoints.
    // Even Springboot throws this error if you try to be confusing
    // - Ambiguous mapping. Cannot map 'testController' method
    private static Method getTargetMethod(Class controllerClass, String methodName, Object[] parameters) throws Exception {
        Method[] methodArray = controllerClass.getDeclaredMethods();
        List<Method> candidateMethods = new LinkedList<>();
        for (int count = 0; count < methodArray.length; count++) {
            Method currentMethod = methodArray[count];
            if ((currentMethod.getName().equals(methodName)) && (currentMethod.getParameterCount() == parameters.length)) {
                candidateMethods.add(currentMethod);
            }
        }
        if (candidateMethods.size() > 1) {
            // Springboot will throw similar error on initialization by default, this is 2nd level safety
            StringBuffer error = new StringBuffer();
            error.append("Polymorphic (same number of parameters) endpoints detected. Fix this!:\n");
            for (int countMethods = 0; countMethods < candidateMethods.size(); countMethods++) {
                error.append(" - " + controllerClass.getSimpleName() + "::" + methodName + "(");
                Parameter[] methodParameters = candidateMethods.get(countMethods).getParameters();
                String[] methodParametersString = new String[methodParameters.length];
                for (int countParameter = 0; countParameter < methodParameters.length; countParameter++) {
                    methodParametersString[countParameter] = methodParameters[countParameter].getType().getName();
                }
                error.append(String.join(",", methodParametersString) + ")\n");
            }
            throw new Exception(error.toString());
        }
        if (candidateMethods.size() == 0) {
            throw new NoSuchMethodException("Target Method = '" + controllerClass.getName() + "." + methodName + "(" + parameters.length + ")'");
        }
        return candidateMethods.get(0);
    }

    public static Object[] getParameterArray(Object... parameters) {
        return parameters;
    }
    public static String[] getParameterNames(String... parameterNames) {
        return parameterNames;
    }
}
