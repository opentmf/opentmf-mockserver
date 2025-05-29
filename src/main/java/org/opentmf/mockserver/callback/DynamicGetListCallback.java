package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.model.TmfConstants.HREF;
import static org.opentmf.mockserver.model.TmfConstants.ID;
import static org.opentmf.mockserver.util.HttpRequestUtil.extractFields;
import static org.opentmf.mockserver.util.HttpRequestUtil.extractFilter;
import static org.opentmf.mockserver.util.HttpRequestUtil.extractLimit;
import static org.opentmf.mockserver.util.HttpRequestUtil.extractOffset;
import static org.opentmf.mockserver.util.HttpRequestUtil.extractSort;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.opentmf.mockserver.model.Id;
import org.opentmf.mockserver.model.RequestContext;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;

/**
 *
 *
 * <h2>DynamicGetListCallback</h2>
 *
 * <ul>
 *   <li>Decides the domain from the path parameter.
 *   <li>Extracts offset, limit, sort criteria, filter and fields from the httpRequest.
 *   <li>Applies jsonPath filter to the cached domain payloads.
 *   <li>Applies sorting to the filtered out domain payloads.
 *   <li>Restricts the set by applying paging depending on the offset and limit.
 *   <li>Applies fields filtering to the payloads to return.
 *   <li>Finds the total result count and sets header X-Total-Count as per TMF-630 specification.
 *   <li>Finds the items' content range and sets header Content-Range as per TMF-630 specification.
 *   <li>Serves the response with http status 200 and content type application/json.
 * </ul>
 *
 * @author Yusuf BOZKURT
 */
public class DynamicGetListCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    RequestContext ctx = RequestContext.initialize(httpRequest, false, null);

    // Retrieve the cached data associated with the domain
    SortedMap<Id, JsonNode> cachedData = CACHE.getAll(ctx.getDomain());

    // Extract limit, offset, sort, and filter parameters from the request
    int limit = extractLimit(httpRequest);
    int offset = extractOffset(httpRequest);
    Set<String> sortList = extractSort(httpRequest);
    String filter = extractFilter(httpRequest);
    Set<String> fields = extractFields(httpRequest);

    // Convert cached data to a list for further processing
    List<JsonNode> jsonNodesBeforeFilter = new ArrayList<>(cachedData.values());

    // Apply filter to the data
    List<JsonNode> afterFiltered = applyFilter(jsonNodesBeforeFilter, filter);

    // Get the total count of filtered data
    long totalCount = afterFiltered.size();

    // Apply sorting, paging, and fields filtering to the filtered data
    List<JsonNode> dataList = applySortingAndPaging(afterFiltered, sortList, offset, limit);
    dataList = applyFieldsFiltering(dataList, fields);

    // Get the count of results after sorting and filtering
    int resultCount = dataList.size();

    // Construct Content-Range header to indicate the range of returned resources
    String contentRange = "items " + (offset + 1) + "-" + (offset + resultCount) + "/" + totalCount;

    // Convert the filtered, sorted, and paginated data to a JSON array
    ArrayNode arrayNode = JacksonUtil.createArrayNode();
    dataList.forEach(arrayNode::add);

    // Generate and return the response containing the filtered, sorted, and paginated resource list
    return HttpResponse.response()
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(JacksonUtil.writeAsString(arrayNode))
        .withHeader("X-Total-Count", String.valueOf(totalCount))
        .withHeader("Content-Range", contentRange);
  }

  private List<JsonNode> applyFilter(List<JsonNode> data, String filter) {
    if (filter == null || filter.isEmpty()) {
      return data;
    }
    String dataAsString = JacksonUtil.writeAsString(data);
    List<Object> filteredData = JsonPath.read(dataAsString, filter);
    return JacksonUtil.convertToJsonNodeList(filteredData);
  }

  private List<JsonNode> applySortingAndPaging(
      List<JsonNode> data, Set<String> sort, int offset, int limit) {

    List<Comparator<JsonNode>> comparators = getComparators(sort);
    Comparator<JsonNode> combinedComparator =
        comparators.stream().reduce(Comparator::thenComparing).orElse((o1, o2) -> 0);

    return data.stream()
        .sorted(combinedComparator)
        .skip(offset)
        .limit(limit)
        .collect(Collectors.toList());
  }

  private List<JsonNode> applyFieldsFiltering(List<JsonNode> data, Set<String> fields) {
    if (fields == null || fields.isEmpty()) {
      return data;
    }
    fields.add(ID);
    fields.add(HREF);

    return data.stream().map(node -> filterFields(node, fields)).collect(Collectors.toList());
  }

  private JsonNode filterFields(JsonNode originalNode, Set<String> fieldNames) {
    ObjectNode filteredNode = JacksonUtil.createObjectNode();
    for (String fieldName : fieldNames) {
      if (originalNode.has(fieldName)) {
        filteredNode.set(fieldName, originalNode.get(fieldName));
      }
    }
    return filteredNode;
  }

  private int compare(String sortField, JsonNode node1, JsonNode node2) {
    if (node1.get(sortField).isNumber() && node2.get(sortField).isNumber()) {
      return Integer.compare(node1.get(sortField).asInt(), node2.get(sortField).asInt());
    } else if (node1.get(sortField).isTextual() && node2.get(sortField).isTextual()) {
      return node1.get(sortField).asText().compareTo(node2.get(sortField).asText());
    } else if (node1.get(sortField).isBoolean() && node2.get(sortField).isBoolean()) {
      return Boolean.compare(node1.get(sortField).asBoolean(), node2.get(sortField).asBoolean());
    } else {
      return node1.get(sortField).asText().compareTo(node2.get(sortField).asText());
    }
  }

  private List<Comparator<JsonNode>> getComparators(Set<String> sortFields) {
    return sortFields.stream()
        .map(
            sortField -> {
              if (sortField.startsWith("-")) {
                String field = sortField.substring(1);
                return (Comparator<JsonNode>) (node1, node2) -> compare(field, node2, node1);
              } else {
                return (Comparator<JsonNode>) (node1, node2) -> compare(sortField, node1, node2);
              }
            })
        .collect(Collectors.toList());
  }
}
