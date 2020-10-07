package com.example.springloomtest;

import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
public class HashingUtil {
    private static List<String> jsonSensitiveFieldList = new LinkedList();
    private static Pattern sensitiveFieldPattern;
    private static List<String> jsonEmptyValues = new LinkedList<>();

    static {
        jsonSensitiveFieldList.add("ssn");
        jsonSensitiveFieldList.add("accountNumber");

        jsonEmptyValues.add("[]");
        jsonEmptyValues.add("{}");
        jsonEmptyValues.add("null");
        jsonEmptyValues.add("");

        List<String> sensitiveFieldListCompiled = new LinkedList<>();
        for (int count = 0; count < jsonSensitiveFieldList.size(); count++) {
            sensitiveFieldListCompiled.add("([\\\\]*)(\"" + jsonSensitiveFieldList.get(count) + ")([\\\\]*)(\":)");
        }
        sensitiveFieldPattern = Pattern.compile(String.join("|", sensitiveFieldListCompiled), Pattern.CASE_INSENSITIVE);
    }

    private static Map<String, MessageDigest> messageDigestMap = new HashMap<>();

    public static String hash(String algorithm, String input) throws Exception {
        MessageDigest messageDigest = messageDigestMap.get(algorithm);
        if (messageDigest == null) {
            messageDigest = MessageDigest.getInstance(algorithm);
            messageDigestMap.put(algorithm, messageDigest);
        }
        byte[] hashInBytes = messageDigest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : hashInBytes) {
            stringBuilder.append(String.format("%02x", b));
        }
        return stringBuilder.toString();
    }

    public static String hashJson(String algorithm, String jsonInput) throws Exception {
        Map<String, Object> tempMap = CoreSingletons.objectMapper.readValue(jsonInput, Map.class);
        List<Integer> beginIndexes = new LinkedList<>();
        List<Integer> endIndexes = new LinkedList<>();
        List<String> finalValues = new LinkedList<>();

        StringBuilder newJsonStringBuilder = new StringBuilder();
        Matcher matcher = sensitiveFieldPattern.matcher(jsonInput);

        while (matcher.find()) {
            int findStartIndex = matcher.end();
            String segment = jsonInput.substring(findStartIndex).trim();
            int findEndIndex = segment.indexOf("}"); // no matter what, JSON always ends with '}'
            int commaIndex = segment.indexOf(",");

            if (segment.startsWith("\\")) {
                //Nested String
                String endingPattern = segment.substring(0, segment.indexOf("\"") + 1);
                findEndIndex = segment.indexOf(endingPattern, endingPattern.length()) + endingPattern.length();
            } else {
                String startMarker = null;
                String endMarker = null;
                if (segment.startsWith("{")) {
                    startMarker = "{";
                    endMarker = "}";
                } else if (segment.startsWith("[")) {
                    startMarker = "[";
                    endMarker = "]";
                } else if (segment.startsWith("\"")) {
                    startMarker = "\"";
                    endMarker = "\"";
                } else {
                    if ((commaIndex > -1) && (commaIndex < findEndIndex)) {
                        findEndIndex = commaIndex;
                    }
                }
                if (startMarker != null) {
                    int countStartMarker = 0;
                    for (int count = 0; count < segment.length(); count++) {
                        if (segment.charAt(count) == startMarker.charAt(0)) {
                            if (startMarker.equalsIgnoreCase(endMarker)) {
                                if (countStartMarker > 0) {
                                    countStartMarker--;
                                } else {
                                    countStartMarker++;
                                }
                            } else {
                                countStartMarker++;
                            }
                        } else if (segment.charAt(count) == endMarker.charAt(0)) {
                            countStartMarker--;
                        }
                        if (countStartMarker == 0) {
                            findEndIndex = count + 1;
                            break;
                        }
                    }
                }
            }

            segment = segment.substring(0, findEndIndex).trim();

            //Left and right always has the same number of quote and escape characters
            while ((segment.length() > 0) && ((segment.charAt(0) == '\\') || (segment.charAt(0) == '"'))) {
                segment = segment.substring(1, segment.length() - 1);
            }

//            System.out.println(jsonInput.substring(matcher.start(), matcher.end()) + "=" + segment);

            int valueBeginIndex = jsonInput.indexOf(segment, findStartIndex);
            int valueEndIndex = valueBeginIndex + segment.length();
            String finalValue = segment;
            if (!jsonEmptyValues.contains(segment)) {
                if (segment.startsWith("[")) {
                    List tempList = CoreSingletons.objectMapper.readValue(segment, List.class);
                    for (int count = 0; count < tempList.size(); count++) {
                        String value = String.valueOf(tempList.get(count));
                        tempList.set(count, HashingUtil.hash(algorithm, value));
                    }
                    finalValue = CoreSingletons.objectMapper.writeValueAsString(tempList);
                } else if (segment.startsWith("{")) {
                    Map<String, Object> tempSegmentMap = CoreSingletons.objectMapper.readValue(segment, Map.class);
                    String key = null;
                    Object value = null;
                    for (Map.Entry<String, Object> entry : tempSegmentMap.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("number")) {
                            key = entry.getKey();
                            value = HashingUtil.hash(algorithm, String.valueOf(entry.getValue()));
                            break;
                        }
                    }
                    if (key != null) {
                        tempSegmentMap.put(key, value);
                    }
                    finalValue = CoreSingletons.objectMapper.writeValueAsString(tempSegmentMap);
                } else {
                    finalValue = HashingUtil.hash(algorithm, segment);
                }
            }
            beginIndexes.add(valueBeginIndex);
            endIndexes.add(valueEndIndex);
            finalValues.add(finalValue);
        }
        if (beginIndexes.size() == 0) {
            newJsonStringBuilder.append(jsonInput);
        } else {
            int lastEnd = -1;
            for (int count = 0; count < beginIndexes.size(); count++) {
                int valueBeginIndex = beginIndexes.get(count);
                int valueEndIndex = endIndexes.get(count);
                String finalValue = finalValues.get(count);
                String pre;
                if (lastEnd == -1) {
                    pre = jsonInput.substring(0, valueBeginIndex);
                } else {
                    pre = jsonInput.substring(lastEnd, valueBeginIndex);
                }
                newJsonStringBuilder.append(pre);
                newJsonStringBuilder.append(finalValue);
                lastEnd = valueEndIndex;
            }
            String post = jsonInput.substring(lastEnd);
            newJsonStringBuilder.append(post);
        }
        return newJsonStringBuilder.toString();
    }

    public static void main(String[] args) {
        try {
//            System.out.println(HashingUtil.hash("MD5", "password1"));
//            System.out.println(HashingUtil.hash("MD5", "password2HashingUtil"));


//            String inputJson = "{\"userId\":\"abcdefg\",\"accountNumber\":\"1234567\",\"ssn\":\"99999\",\"email\":\"something@company.com\"}";
//            Map<String, Object> inputMap = CoreSingletons.objectMapper.readValue(inputJson, Map.class);
//            DeV1LogInfo temp = new DeV1LogInfo();
//            temp.setRequest(inputMap);
//
//            String testJsonInput = CoreSingletons.objectMapper.writeValueAsString(temp);
//            String testJsonOutput = HashingUtil.hashJson("MD5", testJsonInput);
//
//            System.out.println("BEFORE");
//            System.out.println(testJsonInput);
//            System.out.println("AFTER");
//            System.out.println(testJsonOutput);
//            DeV1LogInfo hashedTemp = CoreSingletons.objectMapper.readValue(testJsonOutput, DeV1LogInfo.class);
//            System.out.println(CoreSingletons.objectMapper.writeValueAsString(hashedTemp));
            String inputJson = "{\"ssn\":\"\",\"ssn\":[\"1234\",\"3456\",\"1324\",\"8888\",\"7777\"],\"ssn\":null,\"ssn\":{},\"userId\":\"abcdefg\",\"accountNumber\":\"1234567\",\"ssn\":\"99999\",\"email\":\"something@company.com\", \"nestedJsonString\":\"{\\\"subField1\\\":\\\"{\\\\\\\"accountNumber\\\\\\\":\\\\\\\"secreto\\\\\\\"}\\\",\\\"ssn\\\":\\\"999999999\\\", \\\"ssn2\\\":\\\"123\\\", \\\"accountNumber\\\":\\\"fake\\\"}\"}";
            String testJsonOutput = HashingUtil.hashJson("MD5", inputJson);
            System.out.println("BEFORE");
            System.out.println(inputJson);
            System.out.println("AFTER");
            System.out.println(testJsonOutput);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
