package org.mockserver.lifecycle;

import java.util.List;
import org.mockserver.mock.Expectation;

/**
 * @author jamesdbloom
 */
public interface ExpectationsListener {

    void updated(List<Expectation> expectations);

}
