package org.mockserver.matchers;

import static org.mockserver.character.Character.NEW_LINE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Joiner;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.ObjectMapperFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * @author jamesdbloom
 */
public class JsonStringMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private static final ObjectWriter PRETTY_PRINTER = ObjectMapperFactory.createObjectMapper(true, false);
    private final MockServerLogger mockServerLogger;
    private final String matcher;
    private JsonNode matcherJsonNode;
    private final MatchType matchType;

    JsonStringMatcher(MockServerLogger mockServerLogger, String matcher, MatchType matchType) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        this.matchType = matchType;
    }

    public boolean matches(final MatchDifference context, String matched) {
        boolean result = false;

        try {
            if (StringUtils.isBlank(matcher)) {
                result = true;
            } else {
                List<String> differences = new ArrayList<>();

                try {
                    if (matcherJsonNode == null) {
                        matcherJsonNode = ObjectMapperFactory.createObjectMapper().readTree(matcher);
                    }
                    JsonNode actualNode = ObjectMapperFactory.createObjectMapper().readTree(matched);
                    result = compareNodes(matcherJsonNode, actualNode, "", differences);
                } catch (Throwable throwable) {
                    if (context != null) {
                        context.addDifference(mockServerLogger, throwable, "exception while perform json match failed expected:{}found:{}", this.matcher, matched);
                    }
                }

                if (!result) {
                    if (context != null) {
                        if (differences.isEmpty()) {
                            context.addDifference(mockServerLogger, "json match failed expected:{}found:{}", this.matcher, matched);
                        } else {
                            context.addDifference(mockServerLogger, "json match failed expected:{}found:{}failed because:{}", this.matcher, matched, Joiner.on("," + NEW_LINE).join(differences));
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            if (context != null) {
                context.addDifference(mockServerLogger, throwable, "json match failed expected:{}found:{}failed because:{}", this.matcher, matched, throwable.getMessage());
            }
        }

        return not != result;
    }

    private boolean compareNodes(JsonNode expected, JsonNode actual, String path, List<String> differences) {
        if (expected == null && actual == null) {
            return true;
        }
        if (expected == null || actual == null) {
            differences.add("wrong value at \"" + path + "\", expected: " + prettyPrint(expected) + " but was: " + prettyPrint(actual));
            return false;
        }

        if (expected.isObject() && actual.isObject()) {
            return compareObjects((ObjectNode) expected, (ObjectNode) actual, path, differences);
        }

        if (expected.isArray() && actual.isArray()) {
            return compareArrays((ArrayNode) expected, (ArrayNode) actual, path, differences);
        }

        if (!expected.equals(actual)) {
            differences.add("wrong value at \"" + path + "\", expected: " + prettyPrint(expected) + " but was: " + prettyPrint(actual));
            return false;
        }
        return true;
    }

    private boolean compareObjects(ObjectNode expected, ObjectNode actual, String path, List<String> differences) {
        boolean match = true;

        for (Map.Entry<String, JsonNode> entry : expected.properties()) {
            String fieldName = entry.getKey();
            String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;
            JsonNode actualValue = actual.get(fieldName);

            if (actualValue == null) {
                differences.add("missing element at \"" + fieldPath + "\"");
                match = false;
            } else if (!compareNodes(entry.getValue(), actualValue, fieldPath, differences)) {
                match = false;
            }
        }

        if (matchType == MatchType.STRICT) {
            for (Map.Entry<String, JsonNode> entry : actual.properties()) {
                String fieldName = entry.getKey();
                if (expected.get(fieldName) == null) {
                    String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;
                    differences.add("additional element at \"" + fieldPath + "\" with value: " + prettyPrint(entry.getValue()));
                    match = false;
                }
            }
        }

        return match;
    }

    private boolean compareArrays(ArrayNode expected, ArrayNode actual, String path, List<String> differences) {
        if (matchType == MatchType.STRICT) {
            return compareArraysStrict(expected, actual, path, differences);
        }
        return compareArraysLenient(expected, actual, path, differences);
    }

    private boolean compareArraysStrict(ArrayNode expected, ArrayNode actual, String path, List<String> differences) {
        boolean match = true;

        if (expected.size() != actual.size()) {
            differences.add("wrong value at \"" + path + "\", expected array of size " + expected.size() + " but was " + actual.size());
            match = false;
        }

        int minSize = Math.min(expected.size(), actual.size());
        for (int i = 0; i < minSize; i++) {
            if (!compareNodes(expected.get(i), actual.get(i), path + "[" + i + "]", differences)) {
                match = false;
            }
        }

        return match;
    }

    private boolean compareArraysLenient(ArrayNode expected, ArrayNode actual, String path, List<String> differences) {
        boolean match = true;
        boolean[] actualUsed = new boolean[actual.size()];

        for (int i = 0; i < expected.size(); i++) {
            JsonNode expectedElement = expected.get(i);
            boolean found = false;

            for (int j = 0; j < actual.size(); j++) {
                if (!actualUsed[j]) {
                    List<String> tempDiffs = new ArrayList<>();
                    if (compareNodes(expectedElement, actual.get(j), "", tempDiffs)) {
                        actualUsed[j] = true;
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                differences.add("missing element at \"" + path + "[" + i + "]\", expected: " + prettyPrint(expectedElement));
                match = false;
            }
        }

        return match;
    }

    private String prettyPrint(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return PRETTY_PRINTER.writeValueAsString(value);
        } catch (JacksonException e) {
            return String.valueOf(value);
        }
    }

    public boolean isBlank() {
        return StringUtils.isBlank(matcher);
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
