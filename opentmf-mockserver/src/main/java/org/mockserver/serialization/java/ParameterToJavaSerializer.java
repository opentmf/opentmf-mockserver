package org.mockserver.serialization.java;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.List;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;

/**
 * @author jamesdbloom
 */
public class ParameterToJavaSerializer implements MultiValueToJavaSerializer<Parameter> {
  @Override
  public String serialize(int numberOfSpacesToIndent, Parameter parameter) {
    StringBuilder output = new StringBuilder();
    output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent * INDENT_SIZE, ' '));
    String serializedKey = NottableStringToJavaSerializer.serialize(parameter.getName(), false);
    output.append("new Parameter(").append(serializedKey);
    for (NottableString value : parameter.getValues()) {
      output
          .append(", ")
          .append(NottableStringToJavaSerializer.serialize(value, serializedKey.endsWith(")")));
    }
    output.append(")");
    return output.toString();
  }

  @Override
  public String serializeAsJava(int numberOfSpacesToIndent, List<Parameter> parameters) {
    StringBuilder output = new StringBuilder();
    for (int i = 0; i < parameters.size(); i++) {
      output.append(serialize(numberOfSpacesToIndent, parameters.get(i)));
      if (i < (parameters.size() - 1)) {
        output.append(",");
      }
    }
    return output.toString();
  }

  @Override
  public String serializeAsJava(int numberOfSpacesToIndent, Parameter... object) {
    return serializeAsJava(numberOfSpacesToIndent, Arrays.asList(object));
  }
}
