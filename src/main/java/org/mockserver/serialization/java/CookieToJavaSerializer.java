package org.mockserver.serialization.java;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.List;
import org.mockserver.model.Cookie;

/**
 * @author jamesdbloom
 */
public class CookieToJavaSerializer implements MultiValueToJavaSerializer<Cookie> {
    @Override
    public String serialize(int numberOfSpacesToIndent, Cookie cookie) {
        return NEW_LINE + Strings.padStart("", numberOfSpacesToIndent * INDENT_SIZE, ' ') + "new Cookie(" +
            NottableStringToJavaSerializer.serialize(cookie.getName(), false) + ", " +
            NottableStringToJavaSerializer.serialize(cookie.getValue(), false) + ")";
    }

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, List<Cookie> cookies) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < cookies.size(); i++) {
            output.append(serialize(numberOfSpacesToIndent, cookies.get(i)));
            if (i < (cookies.size() - 1)) {
                output.append(",");
            }
        }
        return output.toString();
    }

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, Cookie... object) {
        return serializeAsJava(numberOfSpacesToIndent, Arrays.asList(object));
    }
}
