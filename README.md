# opentmf-mockserver

This project consists of general purpose TMF-630 compatible dynamic expectation implementations for post, get, patch, and delete on top of [Mock Server Netty](www.mock-server.org).

## Why This Library

- To help the tests and minimize the requirement to create many expectations.
- To provide getToken expectations for openid authentication servers.

## Working Model

- The posted payloads will be cached for two hours, so that get, patch and delete methods can access the original payload within this time frame.
- The cached payload will be evicted if not touched for two hours.
- Get and Patch operations will mean a touch, which will reset the cache evict timer.
- The implementations will take care of setting logical values for id, createdDate, createdBy, updatedDate, updatedBy, revision, and status fields automatically.

## Implementation Details

The following classes have been implemented:

- [DynamicPostCallback.java](src/main/java/org/opentmf/mockserver/callback/DynamicPostCallback.java)
    - If "id" is provided in the payload, checks if that id exists in the payload cache. Returns 400 if so.
    - Uses either the provided id, or generates a new id for the posted payload.
    - Adds createdBy, createdDate and revision fields. Overrides if they are already provided.
    - Removes updatedDate and updatedBy, if they are provided in the payload.
    - Decides the state field name and initial value according to the path.
    - If state (or status) is not provided, sets the state value to the default initial. Here is the state value matrix that matches configured path according to type field:
            
      | Type      | Field name      | Initial Value | Final Value |
      |-----------|-----------------|---------------|-------------|
      | Orders    | state           | acknowledged  | completed   |
      | Inventory | status          | created       | active      |
      | Catalog   | lifecycleStatus | inStudy       | inDesign    |
      | Candidate | lifecycleStatus | inStudy       | inDesign    |
      | Default   | state           | acknowledged  | completed   |

    - If at least two of the state, status and/or lifecycleStatus are provided at the same time, returns 400.
    - Caches the payload, and returns 200.


- [DynamicGetCallback.java](src/main/java/org/opentmf/mockserver/callback/DynamicGetCallback.java)
    - Considers the last path parameter as the id.
    - Checks if a payload is found in the cache with that id.
    - Returns 404 if no payload is cached with that id.
    - If the cached payload is not previously patched, and if its state field is still at initial value, then sets the final value to the state field, and adds updatedDate, updatedBy fields, plus, increases the revision field.
    - Touches the cache, so that the evict timer restarts for this particular payload.
    - Returns 200 and the potentially manipulated payload.


- [DynamicGetListCallback.java](src/main/java/org/opentmf/mockserver/callback/DynamicGetListCallback.java)
    - Decides the domain from the path parameter.
    - Extracts offset, limit, sort criteria, filter and fields from the httpRequest.
    - Applies jsonPath filter to the cached domain payloads.
    - Applies sorting to the filtered out domain payloads.
    - Restricts the set by applying paging depending on the offset and limit.
    - Applies fields filtering to the payloads to return.
    - Finds the total result count and sets header X-Total-Count as per TMF-630 specification.
    - Finds the items content range and sets header Content-Range as per TMF-630 specification.
    - Serves the response with http status 200 and content type application/json.


- [DynamicJsonPatchCallback.java](src/main/java/org/opentmf/mockserver/callback/DynamicJsonPatchCallback.java)
    - Considers the last path parameter as the id.
    - Checks if a payload is found in the cache with that id.
    - Returns 404 if no payload is cached with that id.
    - Applies the jsonPatch body to the cached payload.
    - Updates the cached payload with the patch result and restarts the cache evict timer.
    - Adds/overrides updatedDate, updatedBy fields, plus, increases the revision field's value by one.
    - Returns 200 and the updated payload.


- [DynamicMergePatchCallback.java](src/main/java/org/opentmf/mockserver/callback/DynamicMergePatchCallback.java)
    - Considers the last path parameter as the id.
    - Checks if a payload is found in the cache with that id.
    - Returns 404 if no payload is cached with that id.
    - Applies the mergePatch body to the cached payload.
    - Updates the cached payload with the patch result and restarts the cache evict timer.
    - Adds/overrides updatedDate, updatedBy fields, plus, increases the revision field's value by one.
    - Returns 200 and the updated payload.


- [DynamicDeleteCallback.java](src/main/java/org/opentmf/mockserver/callback/DynamicDeleteCallback.java)
    - Considers the last path parameter as the id.
    - Checks if a payload is found in the cache with that id.
    - Returns 404 if no payload is cached with that id.
    - Removes the cached payload from the cache, with that id.
    - Returns 204 No Content.


- [OpenidTokenCallback.java](src/main/java/org/opentmf/mockserver/callback/OpenidTokenCallback.java)
    - Checks if the payload contains necessary fields depending on the mandatory attribute "grant_type" and returns 400 Bad Request if a required parameter is missing from the request body.
    - Prepares and returns an OpenID token payload with httpStatus = 200.


## Build & Run

### A) Using Standalone MockServer

#### Prepare Standalone MockServer
```shell
mkdir /path/to/mockserver
cd /path/to/mockserver
wget https://repo1.maven.org/maven2/org/mock-server/mockserver-netty-no-dependencies/5.15.0/mockserver-netty-no-dependencies-5.15.0.jar
```

#### Build & Copy Dependencies
```shell
cd /path/to/project 
mvn clean install
cp -r target/libs /path/to/mockserver
cp target/dynamic-mock-expectations.jar path/to/mockserver/libs
cp src/main/config/scripts/run.sh /path/to/mockserver
```

#### Start Standalone MockServer
```shell
# Start the standalone mockserver
cd /path/to/mockserver
./run.sh
```
### B) Using Local Docker Image

```shell
# build the project and auto-create a docker image for mockserver 
mvn -P docker clean package

# run the created docker container
docker run -p 1080:1080 local/mockserver:1.0.0 -serverPort 1080
```

## Create Expectations

### POST /ShToken

Expectation to generate and return a fake WSO2 token.

```shell
# define expectation for POST /token
curl -X PUT http://localhost:1080/mockserver/expectation \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d \
'{
    "httpRequest" : {
        "method": "POST",
        "path" : "/token"
    },
    "httpResponseClassCallback" : {
        "callbackClass" : "callback.mockserver.org.opentmf.OpenidTokenCallback"
    }
}'
```

### POST /serviceOrder
```shell
# define expectation for POST /serviceOrder 
curl -X PUT http://localhost:1080/mockserver/expectation \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d \
'{
    "httpRequest" : {
        "method": "POST",
        "path" : "/tmf-api/serviceOrdering/v4/serviceOrder"
    },
    "httpResponseClassCallback" : {
        "callbackClass" : "callback.mockserver.org.opentmf.DynamicPostCallback"
    }
}'
```
### GET /serviceOrder/{id}
```shell
# define expectation for GET /serviceOrder/{id} 
curl -X PUT http://localhost:1080/mockserver/expectation \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d \
'{
    "httpRequest" : {
        "method": "GET",
        "path" : "/tmf-api/serviceOrdering/v4/serviceOrder/.*"
    },
    "httpResponseClassCallback" : {
        "callbackClass" : "callback.mockserver.org.opentmf.DynamicGetCallback"
    }
}'
```

### GET /serviceOrder
```shell
# define expectation for GET /serviceOrder 
curl -X PUT http://localhost:1080/mockserver/expectation \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d \
'{
    "httpRequest" : {
        "method": "GET",
        "path" : "/tmf-api/serviceOrdering/v4/serviceOrder.*"
    },
    "httpResponseClassCallback" : {
        "callbackClass" : "callback.mockserver.org.opentmf.DynamicGetListCallback"
    }
}'
```

### JSON - PATCH /serviceOrder/{id}
```shell
# define expectation for PATCH /serviceOrder/{id} 
curl -X PUT http://localhost:1080/mockserver/expectation \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d \
'{
    "httpRequest" : {
        "method": "PATCH",
        "headers": {"Content-Type": ["application/json-patch+json"]},
        "path" : "/tmf-api/serviceOrdering/v4/serviceOrder/.*"
    },
    "httpResponseClassCallback" : {
        "callbackClass" : "callback.mockserver.org.opentmf.DynamicJsonPatchCallback"
    }
}'
```

### MERGE - PATCH /serviceOrder/{id}
```shell
# define expectation for PATCH /serviceOrder/{id} 
curl -X PUT http://localhost:1080/mockserver/expectation \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d \
'{
    "httpRequest" : {
        "method": "PATCH",
        "headers": {"Content-Type": ["application/merge-patch+json"]},
        "path": "/tmf-api/serviceOrdering/v4/serviceOrder/.*"
    },
    "httpResponseClassCallback" : {
        "callbackClass" : "callback.mockserver.org.opentmf.DynamicMergePatchCallback"
    }
}'
```

### DELETE /serviceOrder/{id}
```shell
# define expectation for DELETE /serviceOrder/{id} 
curl -X PUT http://localhost:1080/mockserver/expectation \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d \
'{
    "httpRequest" : {
        "method": "DELETE",
        "path" : "/tmf-api/serviceOrdering/v4/serviceOrder/.*"
    },
    "httpResponseClassCallback" : {
        "callbackClass" : "callback.mockserver.org.opentmf.DynamicDeleteCallback"
    }
}'
```

## Test
### POST /openidToken
```shell
# should return a payload with id and state
curl -X POST 'http://localhost:1080/token' \
-H 'Content-Type: application/x-www-form-urlencoded' \
-d 'grant_type=password' \
-d 'username=XXXXXXX' \
-d 'password=XXXXXXX' \
-d 'scope=openid'

# returns
{
    "access_token": "DIglUJL_SvoYq6L7aoIuOnLHz77JoXZkFPTvkpwNU2U.tQlNXyx5k6AmJqVg5XfYYiM_KqV3_vFEltNmNJfRLecUGATpYFA1oc83G8OTUvKD92svP2yXOGvRY6uNHgVEJQ.3_zat_lkhcJO9_FuFIOhhW_ZdalfT42lhmSpokyB6qE",
    "token_type": "Bearer",
    "scope": "openid",
    "refresh_token": "84cf59dc-6e72-4431-832e-dbd1939d6b94",
    "id_token": "BmZmNiAvcw1ISYCO8WybHf2P9uOTBhTeQTExLrtYqsA.NbMMnncRtf44DGscWAzr-C-v1eLoc8KniPE_MZksnH-_XSz1x6nyp4qWkJ-RwiVbJl2tKIdmORCa_cmCLvLX_w.aLjX7Ys1c2TEWvAzR2l7dBvskd6pQQkgI54hstgQp5g",
    "expires_in": 3599
}
```

### POST /serviceOrder
```shell
# should return a payload with id and state
curl -X POST http://localhost:1080/tmf-api/serviceOrdering/v4/serviceOrder \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d '{}'

# returns
{"id":"dce2ce9d-281b-43df-8150-6242c34c8cf7","state":"acknowledged"}
```

### GET /serviceOrder/{id}
```shell
# should return a payload with id and state
curl -i http://localhost:1080/tmf-api/serviceOrdering/v4/serviceOrder/dce2ce9d-281b-43df-8150-6242c34c8cf7 

# returns
{"id":"dce2ce9d-281b-43df-8150-6242c34c8cf7","state":"completed"}
```

### GET /serviceOrder
```shell
# should return a serviceOrder array each having id and state
curl -i http://localhost:1080/tmf-api/serviceOrdering/v4/serviceOrder

# returns
[{"id":"dce2ce9d-281b-43df-8150-6242c34c8cf7","state":"completed"}]
```

### MERGE-PATCH /serviceOrder/{id}
```shell
# should return a payload with id and state
curl -X PATCH http://localhost:1080/tmf-api/serviceOrdering/v4/serviceOrder/dce2ce9d-281b-43df-8150-6242c34c8cf7 \
-H "Content-Type: application/merge-patch+json" \
-H "Accept: application/json" \
-d '{"state": "started"}'

# returns
{"id":"dce2ce9d-281b-43df-8150-6242c34c8cf7","state":"started"}
```

### JSON-PATCH /serviceOrder/{id}
```shell
# should return a payload with id and state
curl -X PATCH http://localhost:1080/tmf-api/serviceOrdering/v4/serviceOrder/dce2ce9d-281b-43df-8150-6242c34c8cf7 \
-H "Content-Type: application/json-patch+json" \
-H "Accept: application/json" \
-d '[{"op": "replace","path": "/state","value": "started"}]'

# returns
{"id":"dce2ce9d-281b-43df-8150-6242c34c8cf7","state":"started"}
```

### DELETE /serviceOrder/{id}
```shell
# should return no content
curl -i http://localhost:1080/tmf-api/serviceOrdering/v4/serviceOrder/dce2ce9d-281b-43df-8150-6242c34c8cf7

# returns 
HTTP 204, No Content
```

## Release Notes
### 1.0.0
- Initial Release
### 1.0.1
- Started supporting versioned entities. TMF-630 Part 4.2
### 1.0.2
- The first open-source version
### 1.0.3
- Enhanced the version resolving algorithm
