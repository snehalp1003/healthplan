/**
 * 
 */
package com.me.healthplan.rabbitmq;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * @author Snehal Patel
 */

@Component
public class IndexingListener {

    private CountDownLatch latch = new CountDownLatch(1);

    Map<String, String> relMap = new HashMap<>();

    public void receiveMessage(Map<String, String> message) {
        System.out.println("Message received: " + message);
        String operation = message.get("operation");
        String uri = message.get("uri");
        String body = message.get("body");
        String indexName = message.get("index");
        boolean isChild = Boolean.valueOf(message.get("isChild"));
        String currentObjId = message.get("id");
        JSONObject jsonBody = new JSONObject(body);

        switch (operation) {
        case "SAVE": {
            if (!checkIndexMapping(uri, indexName, jsonBody)) {
                this.createIndexMapping(uri, indexName, jsonBody);
            }
            indexJsonObj(uri, indexName, jsonBody, currentObjId, isChild);
            break;
        }
        case "DELETE": { // todo delete the document, not the index
            this.deleteIndex(uri, indexName, jsonBody);
            break;
        }
        // TODO update
        }
    }

    private int executeRequest(HttpUriRequest request) {
        int result = 0;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse response = httpClient.execute(request)) {
            System.out
                    .println("ElasticSearch Response: " + response.toString());
            result = response.getStatusLine().getStatusCode();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private boolean checkIndexMapping(String uri, String indexName,
            JSONObject objectBody) {
        // check if mapping is present
        HttpUriRequest request = new HttpGet(
                uri + "/" + indexName + "/_mapping");
        if (executeRequest(request) == HttpStatus.SC_NOT_FOUND) {
            return false;
        } else {
            return true;
        }
    }

    private void createIndexMapping(String uri, String indexName,
            JSONObject objectBody) {
        JSONObject mappingObj = new JSONObject("         {\"mappings\": {"
                + "             \"properties\":{ "
                + "              \"objectId\":{"
                + "               \"type\": \"keyword\"},"
                + "               \"plan_service\": {"
                + "                 \"type\":\"join\","
                + "                 \"relations\":{"
                + "                   \"plan\": ["
                + "                     \"membercostshare\","
                + "                     \"planservice\""
                + "                     ],"
                + "                     \"planservice\":["
                + "                       \"service\","
                + "                       \"planservice_membercostshare\""
                + "                       ]" + "                 }"
                + "               }" + "             }" + "           }"
                + "         }" + "       }");
        System.out.println("Mapping Object: " + mappingObj.toString());

        HttpPut request = new HttpPut(uri + "/" + indexName);
        request.addHeader(HttpHeaders.CONTENT_TYPE,
                String.valueOf(ContentType.APPLICATION_JSON));
        try {
            request.setEntity(new StringEntity(mappingObj.toString()));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        this.executeRequest(request);
    }

    private void indexObject(String uri, String indexName,
            JSONObject objectBody) {
        HttpPut request = new HttpPut(uri + "/" + indexName + "/_doc/"
                + objectBody.getString("objectId"));
        request.addHeader(HttpHeaders.CONTENT_TYPE,
                String.valueOf(ContentType.APPLICATION_JSON));
        try {
            request.setEntity(new StringEntity(objectBody.toString()));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        this.executeRequest(request);
    }

//    POST /planindex/_doc/12xvxc345ssdsds-508  -> Parent 
//    POST /planindex/_doc/1234vxc2324sdf-501?routing=1 -> Child
    private void indexJsonObj(String uri, String indexName, JSONObject json,
            String id, boolean isChild) {
        String url = "";
        if (isChild) {
            url = uri + "/" + indexName + "/_doc/" + id + "?routing=1";
        } else {
            url = uri + "/" + indexName + "/_doc/" + id;
        }
        if (!url.isEmpty()) {
            HttpPost request = new HttpPost(url);
            request.addHeader(HttpHeaders.CONTENT_TYPE,
                    String.valueOf(ContentType.APPLICATION_JSON));
            try {
                request.setEntity(new StringEntity(json.toString()));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            this.executeRequest(request);
        }
    }

    private void deleteIndex(String uri, String indexName,
            JSONObject objectBody) {
        HttpDelete request = new HttpDelete(uri + "/" + indexName + "/_doc/"
                + objectBody.getString("objectId"));

        this.executeRequest(request);
    }

    public CountDownLatch getLatch() {
        return latch;
    }

}
