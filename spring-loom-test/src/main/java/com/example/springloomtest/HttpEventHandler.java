package com.example.springloomtest;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

@Log4j2
public class HttpEventHandler {

    private static SimpleDateFormat s3DateFormatter;

    static {
        s3DateFormatter = new SimpleDateFormat("yyyy/MM/dd/HH/mmss-SSS");
        s3DateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    //No need kafka at the moment, use fiber for async sending to S3 instead
//    private static int kafka_maxCharacter = 10000;

    public static void requestInterceptor(Method method, Map<String, Object> parameterMap) throws Exception {
        String requestId = UUID.randomUUID().toString();
        Date dateBegin = new Date();
        parameterMap.put("dateBegin", new Date());
        parameterMap.put("requestId", s3DateFormatter.format(dateBegin) + "_" + requestId);
        if (parameterMap.get("parameterCache") != null) {
            ((Map) parameterMap.get("parameterCache")).put("requestId", requestId);
        }
    }

    public static void responseInterceptor(Method method, Map<String, Object> parameterMap) throws Exception {
        String requestId = (String) parameterMap.get("requestId");
        Date dateBegin = (Date) parameterMap.get("dateBegin");
        Date dateEnd = new Date();
        long durationMs = dateEnd.getTime() - dateBegin.getTime();
        String path = getEndpointPath(method, parameterMap);
        DeV1LogInfo logInfo = new DeV1LogInfo();
        logInfo.setHttpStatus((Integer) parameterMap.get("_httpStatus"));
        logInfo.setUrl(path);
        logInfo.setClassName(method.getDeclaringClass().getName());
        logInfo.setMethodName(method.getName());
        logInfo.setRequestId(requestId);
        logInfo.setStatus("ok");
        logInfo.getDetails().put("durationMs", durationMs);
        logInfo.setTimestamp(dateBegin);
        logInfo.setMethod(getEndpointHttpMethod(method));

        Map<String, Object> requestObject = (Map<String, Object>) parameterMap.get("_request");
        try {
            Object body = requestObject.get("body");
            String bodyString = CoreSingletons.objectMapper.writeValueAsString(body);
            Map<String, Object> bodyMap = CoreSingletons.objectMapper.readValue(bodyString, Map.class);
            String inputJsonString = (String) bodyMap.get("inputJson");
            Map<String, Object> inputJsonMap = CoreSingletons.objectMapper.readValue(inputJsonString, Map.class);
            requestObject.put("inputJsonParsed", inputJsonMap);
        } catch (Exception e) {
            //ignore NPE & json parsing error here. If it exist it means either the input doesnt contain inputJson or the json is deformed
        }
        DeV1FormGeneralResponse responseObject = (DeV1FormGeneralResponse) parameterMap.get("_response");
        responseObject.setRequestId(requestId);

        Map<String, Object> parameterCache = (Map<String, Object>) requestObject.get("parameterCache");
        requestObject.remove("parameterCache");

        if (parameterCache != null) {
            Map<String, Map<String, Object>> profileData = (Map<String, Map<String, Object>>) parameterCache.get("profileData");
            logInfo.setProfileData(profileData);

            String rulesetName = (String) parameterCache.get("rulesetName");
            logInfo.setRulesetName(rulesetName);

            String rulesetVersion = (String) parameterCache.get("rulesetVersion");
            logInfo.setRulesetVersion(rulesetVersion);
        }

        logInfo.setRequest(requestObject);

        //Extra processing to prepare redshift's piping
        Map<String, Map<String, Object>> responseProfileData = null;
        Map<String, Object> checkResultOld = null;
        Map<String, Object> checkResultNew = new LinkedHashMap<>();

        String responseString = CoreSingletons.objectMapper.writeValueAsString(responseObject);
        logInfo.setResponse(CoreSingletons.objectMapper.readValue(responseString, Map.class));
        DeV1LogInfo hasedLogInfo = hashSensitiveInfomation(logInfo);
        String logString = CoreSingletons.objectMapper.writeValueAsString(hasedLogInfo);
        if ((logString.contains("Exception")) || (logString.contains("Caused by"))) {
            log.error(logString);
        } else {
            log.info(logString);
        }

        if (logInfo.getRequestId() != null) {
            saveLogToS3Async(logString);
        }
    }

    public static void errorInterceptor(Method method, Map<String, Object> parameterMap) throws Exception {
        String requestId = (String) parameterMap.get("requestId");
        Date dateBegin = (Date) parameterMap.get("dateBegin");
        Date dateEnd = new Date();
        long durationMs = dateEnd.getTime() - dateBegin.getTime();
        String path = getEndpointPath(method, parameterMap);
        DeV1LogInfo logInfo = new DeV1LogInfo();
        logInfo.setHttpStatus((Integer) parameterMap.get("_httpStatus"));
        logInfo.setUrl(path);
        logInfo.setClassName(method.getDeclaringClass().getName());
        logInfo.setMethodName(method.getName());
        logInfo.setRequestId(requestId);
        logInfo.setStatus("error");
        logInfo.getDetails().put("durationMs", durationMs);
        logInfo.setTimestamp(dateBegin);
        logInfo.setMethod(getEndpointHttpMethod(method));

        Throwable throwable = (Throwable) parameterMap.get("_error");
        Throwable rootCause = ExceptionUtils.getRootCause(throwable);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        String stackTrace = sw.toString(); // stack trace as a string

        if (rootCause != null) {
            logInfo.getError().put("message", rootCause.getMessage());
        } else {
            logInfo.getError().put("message", throwable.getMessage());
        }
        logInfo.getError().put("stackTrace", stackTrace);

        Map<String, Object> requestObject = (Map<String, Object>) parameterMap.get("_request");
        try {
            Object body = requestObject.get("body");
            String bodyString = CoreSingletons.objectMapper.writeValueAsString(body);
            Map<String, Object> bodyMap = CoreSingletons.objectMapper.readValue(bodyString, Map.class);
            String inputJsonString = (String) bodyMap.get("inputJson");
            Map<String, Object> inputJsonMap = CoreSingletons.objectMapper.readValue(inputJsonString, Map.class);
            requestObject.put("inputJsonParsed", inputJsonMap);
        } catch (Exception e) {
            //ignore NPE & json parsing error here. If it exist it means either the input doesnt contain inputJson or the json is deformed
        }

        Map<String, Object> parameterCache = (Map<String, Object>) requestObject.get("parameterCache");
        requestObject.remove("parameterCache");

        if (parameterCache != null) {
            Map<String, Map<String, Object>> profileData = (Map<String, Map<String, Object>>) parameterCache.get("profileData");
            logInfo.setProfileData(profileData);

            String rulesetName = (String) parameterCache.get("rulesetName");
            logInfo.setRulesetName(rulesetName);

            String rulesetVersion = (String) parameterCache.get("rulesetVersion");
            logInfo.setRulesetVersion(rulesetVersion);
        }

        logInfo.setRequest(requestObject);
        if (logInfo.getCheckResult().size() > 0) {
            Iterator<Map.Entry<String, Object>> iterator = logInfo.getCheckResult().entrySet().iterator();
            Map.Entry<String, Object> lastElement = null;
            while (iterator.hasNext()) {
                lastElement = iterator.next();
            }
            logInfo.setLastCheckValue(lastElement.getValue());
        }
        DeV1LogInfo hasedLogInfo = hashSensitiveInfomation(logInfo);
        String logString = CoreSingletons.objectMapper.writeValueAsString(hasedLogInfo);
        log.error(logString);

        if (logInfo.getRequestId() != null) {
            saveLogToS3Async(logString);
        }

    }

    private static void saveLogToS3Async(String payload) {
        Fiber fiber = Fiber.schedule(new Runnable() {
            public void run() {
                try {
                    String decisionEngine_endpointLoggingS3BucketName = CoreSingletons.environment.getProperty("cloud.aws.s3.bucketName");
                    DeV1LogInfo payloadObjectMap = CoreSingletons.objectMapper.readValue(payload, DeV1LogInfo.class);

                    String filename = payloadObjectMap.getUrl() + "/" + payloadObjectMap.getRequestId();
                    if (filename.charAt(0) == '/') {
                        filename = filename.substring(1);
                    }
                    payloadObjectMap.setS3Link("https://" + decisionEngine_endpointLoggingS3BucketName + ".s3.amazonaws.com/" + filename);
                    log.info("Saving '" + filename + "' into S3 bucket '" + decisionEngine_endpointLoggingS3BucketName + "'...");
                } catch (Exception e) {
                    log.error("Error while saving to S3", e);
                }
            }
        });
    }

    private static Method getRequestMappingMethod(Method targetMethod) throws Exception {
        Method result = null;
        Class controllerClass = targetMethod.getDeclaringClass();
        Method[] methods = controllerClass.getDeclaredMethods();

        for (int count = 0; count < methods.length; count++) {
            if (methods[count].getName().equals(targetMethod.getName()) && (methods[count].getAnnotation(RequestMapping.class) != null)) {
                result = methods[count];
                break;
            }
        }
        return result;
    }

    private static String getEndpointHttpMethod(Method method) throws  Exception {
        String result;
        Method requestMappingMethod = getRequestMappingMethod(method);

        Annotation methodRequestMappingAnnotation = requestMappingMethod.getAnnotation(RequestMapping.class);
        Class methodRequestMappingAnnotationType = methodRequestMappingAnnotation.annotationType();
        Method methodRequestMappingAnnotationMethodMethod = methodRequestMappingAnnotationType.getMethod("method");
        RequestMethod[] requestMethods = (RequestMethod[]) methodRequestMappingAnnotationMethodMethod.invoke(methodRequestMappingAnnotation);
        RequestMethod requestMethod = requestMethods[0];
        result = requestMethod.name();
        return result;
    }

    private static String getEndpointPath(Method method, Map<String, Object> parameterMap) throws Exception {
        String result = "";
        Class controllerClass = method.getDeclaringClass();
        Annotation controllerRequestMappingAnnotation = controllerClass.getAnnotation(RequestMapping.class);
        Class controllerRequestMappingAnnotationType = controllerRequestMappingAnnotation.annotationType();
        Method controllerRequestMappingAnnotationValueMethod = controllerRequestMappingAnnotationType.getMethod("value");
        String[] controllerPathValue = (String[]) controllerRequestMappingAnnotationValueMethod.invoke(controllerRequestMappingAnnotation);
        result += controllerPathValue[0];

        Method requestMappingMethod = getRequestMappingMethod(method);

        Annotation methodRequestMappingAnnotation = requestMappingMethod.getAnnotation(RequestMapping.class);
        Class methodRequestMappingAnnotationType = methodRequestMappingAnnotation.annotationType();
        Method methodRequestMappingAnnotationValueMethod = methodRequestMappingAnnotationType.getMethod("value");
        String[] methodPathValue = (String[]) methodRequestMappingAnnotationValueMethod.invoke(methodRequestMappingAnnotation);
        result += methodPathValue[0];

        while (result.contains("{")) {
            String resolvedPath = result.substring(0, result.indexOf("{"));
            String pathVariableName = result.substring(result.indexOf("{") + 1, result.indexOf("}"));
            resolvedPath += parameterMap.get(pathVariableName);
            resolvedPath += result.substring(result.indexOf("}") + 1);
            result = resolvedPath;
        }

        return result;
    }

    public static DeV1LogInfo hashSensitiveInfomation(DeV1LogInfo logInfo) throws Exception {

        DeV1LogInfo result = logInfo;
        try {
            String logInfoString = CoreSingletons.objectMapper.writeValueAsString(logInfo);
            logInfoString = HashingUtil.hashJson(CoreSingletons.hashAlgorithm, logInfoString);
            result = CoreSingletons.objectMapper.readValue(logInfoString, DeV1LogInfo.class);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stackTrace = sw.toString(); // stack trace as a string
            logInfo.getError().put("hashSensitiveInfomation_message", e.getMessage());
            logInfo.getError().put("hashSensitiveInfomation_stackTrace", stackTrace);
        }
        return result;
    }
}
