/**
 * 
 */
package com.me.healthplan.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.ws.rs.BadRequestException;

import org.everit.json.schema.ValidationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.me.healthplan.HealthplanApplication;
import com.me.healthplan.service.HealthPlanAuthorizationService;
import com.me.healthplan.service.HealthPlanService;
import com.me.healthplan.utility.JsonValidator;
/**
 * @author Snehal Patel
 */
@RestController
@RequestMapping(path = "/")
public class HealthPlanController {

    @Autowired
    HealthPlanService healthPlanService;

    @Autowired
    JsonValidator jsonValidator;

    @Autowired
    HealthPlanAuthorizationService healthPlanAuthorizationService;

    @Autowired
    private RabbitTemplate template;

    @GetMapping(path = "token")
    public ResponseEntity<Object> getToken()
            throws UnsupportedEncodingException, NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException,
            BadPaddingException {
        String token;
        try {
            token = healthPlanAuthorizationService.generateToken();
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new JSONObject().put("token", token).toString());
    }

    @PostMapping(path = "plan", produces = "application/json")
    public ResponseEntity<Object> createHealthPlan(
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) String healthPlan)
            throws Exception, JSONException {

        // Authorize request
        String authorization = headers.getFirst("Authorization");
        if (authorization == null || authorization.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject().put("Error", "Empty Token !").toString());
        }

        String isValid = healthPlanAuthorizationService
                .authorize(authorization);

        if (isValid.equals("Improper Format of Token"))
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED).body(
                            new JSONObject()
                                    .put("Authetication Error: ",
                                            "Improper Format of Token !")
                                    .toString());

        if (isValid.equals("Invalid Token"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject()
                            .put("Authetication Error: ", "Invalid Token !")
                            .toString());

        if (isValid.equals("Token has expired"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject()
                            .put("Authetication Error: ", "Token has expired !")
                            .toString());

        // Null & Empty Checks
        if (healthPlan == null || healthPlan.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject()
                            .put("Error", "Empty Body in Request !")
                            .toString());
        }
        JSONObject jsonBody = new JSONObject(healthPlan);

        // Validating JSON Body
        try {
            jsonValidator.validateJson(jsonBody);
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("Error",
                            e.getErrorMessage().toString()));
        }

        String planKey = jsonBody.get("objectType").toString() + "_"
                + jsonBody.get("objectId").toString();

        if (healthPlanService.checkIfPlanExists(planKey)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new JSONObject()
                            .put("Message", "Health Plan already exists!")
                            .toString());
        }

        String newEtag = healthPlanService.savePlanToRedis(jsonBody, planKey);

        // break into objects and send to queue
        sendToIndex(new JSONObject(healthPlan), "");

        return ResponseEntity.status(HttpStatus.CREATED).eTag(newEtag)
                .body(" {\"message\": \"Created data with key: "
                        + jsonBody.get("objectId") + "\" }");
    }

    @GetMapping(path = "plan/{objectId}/{type}", produces = "application/json")
    public ResponseEntity<Object> getHealthPlan(
            @RequestHeader HttpHeaders headers, @PathVariable String objectId,
            @PathVariable String type) throws Exception, JSONException {

        // Authorize request
        String authorization = headers.getFirst("Authorization");
        if (authorization == null || authorization.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject().put("Error", "Empty Token !").toString());
        }

        String isValid = healthPlanAuthorizationService
                .authorize(authorization);

        if (isValid.equals("Improper Format of Token"))
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED).body(
                            new JSONObject()
                                    .put("Authetication Error: ",
                                            "Improper Format of Token !")
                                    .toString());

        if (isValid.equals("Invalid Token"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject()
                            .put("Authetication Error: ", "Invalid Token !")
                            .toString());

        if (isValid.equals("Token has expired"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject()
                            .put("Authetication Error: ", "Token has expired !")
                            .toString());

        // Check if plan exists
        if (!healthPlanService.checkIfKeyExists(type + "_" + objectId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject()
                            .put("Message", "ObjectId does not exist")
                            .toString());
        }

        String actualEtag = null;
        if (type.equals("plan")) {
            actualEtag = healthPlanService.getEtag(type + "_" + objectId,
                    "eTag");
            String eTag = headers.getFirst("If-None-Match");
            if (eTag != null && eTag.equals(actualEtag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(actualEtag).build();
            }
        }

        // Form key and get plan
        String key = type + "_" + objectId;
        Map<String, Object> plan = healthPlanService.getPlan(key);

        if (type.equals("plan")) {
            return ResponseEntity.ok().eTag(actualEtag)
                    .body(new JSONObject(plan).toString());
        }

        return ResponseEntity.ok().body(new JSONObject(plan).toString());
    }

    @DeleteMapping(path = "plan/{objectId}", produces = "application/json")
    public ResponseEntity<Object> deleteHealthPlan(
            @RequestHeader HttpHeaders headers, @PathVariable String objectId)
            throws Exception {

        // Authorize request
        String authorization = headers.getFirst("Authorization");
        if (authorization == null || authorization.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject().put("Error", "Empty Token !").toString());
        }

        String isValid = healthPlanAuthorizationService
                .authorize(authorization);

        if (isValid.equals("Improper Format of Token"))
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED).body(
                            new JSONObject()
                                    .put("Authetication Error: ",
                                            "Improper Format of Token !")
                                    .toString());

        if (isValid.equals("Invalid Token"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject()
                            .put("Authetication Error: ", "Invalid Token !")
                            .toString());

        if (isValid.equals("Token has expired"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject()
                            .put("Authetication Error: ", "Token has expired !")
                            .toString());

        // Check if plan exists
        if (!healthPlanService.checkIfKeyExists("plan" + "_" + objectId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject()
                            .put("Message", "ObjectId does not exist")
                            .toString());
        }

        // Form key and delete plan
        healthPlanService.deletePlan("plan" + "_" + objectId);
        return ResponseEntity.ok().body(
                " {\"message\": \"Deleted plan successfully for objectId : "
                        + objectId + "\" }");
    }

    @PatchMapping(path = "/plan/{objectId}", produces = "application/json")
    public ResponseEntity<Object> patchHealthPlan(
            @RequestHeader HttpHeaders headers, @RequestBody String healthPlan,
            @PathVariable String objectId) throws IOException {

        // Authorize request
        String authorization = headers.getFirst("Authorization");
        if (authorization == null || authorization.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject().put("Error", "Empty Token !").toString());
        }

        String isValid = healthPlanAuthorizationService
                .authorize(authorization);

        if (isValid.equals("Improper Format of Token"))
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED).body(
                            new JSONObject()
                                    .put("Authetication Error: ",
                                            "Improper Format of Token !")
                                    .toString());

        if (isValid.equals("Invalid Token"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject()
                            .put("Authetication Error: ", "Invalid Token !")
                            .toString());

        if (isValid.equals("Token has expired"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject()
                            .put("Authetication Error: ", "Token has expired !")
                            .toString());

        // Null & Empty Checks
        if (healthPlan == null || healthPlan.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject()
                            .put("Error", "Empty Body in Request !")
                            .toString());
        }

        JSONObject jsonBody = new JSONObject(healthPlan);

        // Check if plan exists
        String key = "plan_" + objectId;
        if (!healthPlanService.checkIfKeyExists(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject()
                            .put("message", "ObjectId does not exist")
                            .toString());
        }

        // Get eTag value
        String actualEtag = healthPlanService.getEtag(key, "eTag");
        String eTag = headers.getFirst("If-Match");

        if (eTag == null || eTag.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("message", "eTag missing !!")
                            .toString());
        }

        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .eTag(actualEtag).build();
        }

        // Perform patch
        String newEtag = healthPlanService.savePlanToRedis(jsonBody, key);
        
        sendToIndex(new JSONObject(healthPlan), "");

        return ResponseEntity.ok().eTag(newEtag)
                .body(new JSONObject()
                        .put("message: ",
                                "Resource updated successfully on Patch")
                        .toString());
    }

    @PutMapping(path = "/plan/{objectId}", produces = "application/json")
    public ResponseEntity<Object> updatePlan(@RequestHeader HttpHeaders headers,
            @RequestBody String healthPlan, @PathVariable String objectId)
            throws IOException {

        // Authorize request
        String authorization = headers.getFirst("Authorization");
        if (authorization == null || authorization.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject().put("Error", "Empty Token !").toString());
        }

        String isValid = healthPlanAuthorizationService
                .authorize(authorization);

        if (isValid.equals("Improper Format of Token"))
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED).body(
                            new JSONObject()
                                    .put("Authetication Error: ",
                                            "Improper Format of Token !")
                                    .toString());

        if (isValid.equals("Invalid Token"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject()
                            .put("Authetication Error: ", "Invalid Token !")
                            .toString());

        if (isValid.equals("Token has expired"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject()
                            .put("Authetication Error: ", "Token has expired !")
                            .toString());

        // Null & Empty Checks
        if (healthPlan == null || healthPlan.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject()
                            .put("Error", "Empty Body in Request !")
                            .toString());
        }

        JSONObject jsonBody = new JSONObject(healthPlan);

        // Validating JSON Body
        try {
            jsonValidator.validateJson(jsonBody);
        } catch (ValidationException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject().put("Error", ex.getMessage()).toString());
        }

        // Check if plan exists
        String key = "plan_" + objectId;
        if (!healthPlanService.checkIfKeyExists(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject()
                            .put("Message", "ObjectId does not exist")
                            .toString());
        }

        // Get eTag value
        String actualEtag = healthPlanService.getEtag(key, "eTag");
        String eTag = headers.getFirst("If-Match");

        if (eTag == null || eTag.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("message", "eTag missing !!")
                            .toString());
        }

        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .eTag(actualEtag).build();
        }

        // Delete old plan
        healthPlanService.deletePlan(key);

        String newEtag = healthPlanService.savePlanToRedis(jsonBody, key);

        // index object
        Map<String, String> actionMap = new HashMap<>();
        actionMap.put("operation", "SAVE");
        actionMap.put("uri", "http://localhost:9200");
        actionMap.put("index", "planindex");
        actionMap.put("body", healthPlan);

        System.out.println("Sending message: " + actionMap);

        template.convertAndSend(HealthplanApplication.MESSAGE_QUEUE, actionMap);

        return ResponseEntity.ok().eTag(newEtag)
                .body(new JSONObject()
                        .put("message: ",
                                "Resource updated successfully on Put")
                        .toString());
    }

    public void sendToIndex(JSONObject json, String pid) {
        try {
            JSONObject newJson = new JSONObject();

            for (Object key : json.keySet()) {
                String attributeKey = String.valueOf(key);
                Object attributeVal = json.get(String.valueOf(key));
                Object id = json.get("objectId");
                if (attributeKey.equals("planserviceCostShares")) {
                    JSONObject obj = (JSONObject) attributeVal;
                    obj.put("objectType", "planservice_membercostshare");
                    attributeVal = obj;
                }
                if (attributeVal instanceof JSONObject) {
                    sendToIndex((JSONObject) attributeVal, (String) id);
                } else if (attributeVal instanceof JSONArray) {
                    JSONArray arr = json.getJSONArray((String) key);
                    for (int i = 0; i < arr.length(); i++) {
                        sendToIndex(arr.getJSONObject(i), (String) id);
                    }
                } else {
                    newJson.put(attributeKey, attributeVal);
                }
            }

            JSONObject emd = new JSONObject();
            boolean isChild = false;
            emd.put("name", (String) json.get("objectType"));
            if (pid.length() != 0) {
                emd.put("parent", pid);
                isChild = true;
            }
            newJson.put("plan_service", emd);
            String id = json.getString("objectId");
            System.out.println("NEW JSON:\n" + newJson.toString());
            publishToRabbitMq(newJson.toString(), id, isChild);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void publishToRabbitMq(String json, String id, boolean isChild) {
        // index objects
        Map<String, String> actionMap = new HashMap<>();
        actionMap.put("operation", "SAVE");
        actionMap.put("uri", "http://localhost:9200");
        actionMap.put("index", "planindex");
        actionMap.put("id", id);
        actionMap.put("isChild", String.valueOf(isChild));
        actionMap.put("body", json);
        template.convertAndSend(HealthplanApplication.topicExchange,
                "snehal.indexing.queue", actionMap);
        System.out.println("Sending message: " + actionMap);
    }
}
