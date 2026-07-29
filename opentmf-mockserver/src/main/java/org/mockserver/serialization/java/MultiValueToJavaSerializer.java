package org.mockserver.serialization.java;

import java.util.List;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public interface MultiValueToJavaSerializer<T extends ObjectWithReflectiveEqualsHashCodeToString>
    extends ToJavaSerializer<T> {

  String serializeAsJava(int numberOfSpacesToIndent, List<T> object);

  @SuppressWarnings("unchecked")
  String serializeAsJava(int numberOfSpacesToIndent, T... object);
}
