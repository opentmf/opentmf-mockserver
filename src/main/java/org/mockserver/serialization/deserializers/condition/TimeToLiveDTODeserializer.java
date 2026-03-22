package org.mockserver.serialization.deserializers.condition;

import java.util.concurrent.TimeUnit;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.serialization.model.TimeToLiveDTO;
import org.slf4j.event.Level;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * @author jamesdbloom
 */
public class TimeToLiveDTODeserializer extends StdDeserializer<TimeToLiveDTO> {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger();

    public TimeToLiveDTODeserializer() {
        super(TimeToLiveDTO.class);
    }

    @Override
    public TimeToLiveDTO deserialize(JsonParser jsonParser, DeserializationContext ctxt) {
        TimeToLiveDTO timeToLiveDTO = null;
        TimeToLive timeToLive = null;
        TimeUnit timeUnit;
        long ttl = 0L;
        long endDate;
        boolean unlimited = false;

        JsonNode timeToLiveDTONode = ctxt.readTree(jsonParser);
        JsonNode unlimitedNode = timeToLiveDTONode.get("unlimited");
        if (unlimitedNode != null) {
            unlimited = unlimitedNode.asBoolean();
        }
        if (!unlimited) {
            JsonNode timeToLiveNode = timeToLiveDTONode.get("timeToLive");
            if (timeToLiveNode != null) {
                ttl = timeToLiveNode.asLong();
            }
            JsonNode timeUnitNode = timeToLiveDTONode.get("timeUnit");
            if (timeUnitNode != null) {
                try {
                    timeUnit = Enum.valueOf(TimeUnit.class, timeUnitNode.asText());
                    timeToLive = TimeToLive.exactly(timeUnit, ttl);
                } catch (IllegalArgumentException iae) {
                    if (MockServerLogger.isEnabled(Level.TRACE)) {
                        MOCK_SERVER_LOGGER.logEvent(
                            new LogEntry()
                                .setLogLevel(Level.TRACE)
                                .setMessageFormat("exception parsing TimeToLiveDTO timeUnit")
                                .setThrowable(iae)
                        );
                    }
                }
            }
            if (timeToLive != null) {
                JsonNode endDateNode = timeToLiveDTONode.get("endDate");
                if (endDateNode != null) {
                    endDate = endDateNode.asLong();
                    timeToLive.setEndDate(endDate);
                }
                timeToLiveDTO = new TimeToLiveDTO(timeToLive);
            }
        } else {
            timeToLiveDTO = new TimeToLiveDTO(TimeToLive.unlimited());
        }

        return timeToLiveDTO;
    }
}
