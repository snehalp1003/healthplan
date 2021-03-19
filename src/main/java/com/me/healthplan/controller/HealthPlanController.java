/**
 * 
 */
package com.me.healthplan.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.everit.json.schema.ValidationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping(path = "token")
    public ResponseEntity<Object> getToken()
            throws UnsupportedEncodingException, NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException,
            BadPaddingException {
        String token = healthPlanAuthorizationService.generateToken();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                " {\"Status\": \"Successful\" , \"Token\": " + token + "\" }");
    }

    @PostMapping(path = "plan", produces = "application/json")
    public ResponseEntity<Object> createHealthPlan(
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) String healthPlan)
            throws Exception, JSONException {

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

        return ResponseEntity.status(HttpStatus.CREATED).eTag(newEtag)
                .body(" {\"message\": \"Created data with key: "
                        + jsonBody.get("objectId") + "\" }");
    }

    @GetMapping(path = "plan/{objectId}/{type}", produces = "application/json")
    public ResponseEntity<Object> getHealthPlan(
            @RequestHeader HttpHeaders headers, @PathVariable String objectId,
            @PathVariable String type) throws Exception, JSONException {

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

        JSONObject jsonBody = new JSONObject(healthPlan);

        if (!healthPlanService.checkIfKeyExists("plan_" + objectId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject()
                            .put("message", "ObjectId does not exist")
                            .toString());
        }

        String key = "plan_" + objectId;
        String newEtag = healthPlanService.savePlanToRedis(jsonBody, key);

        return ResponseEntity.ok().eTag(newEtag).body(new JSONObject()
                .put("message: ", "Resource updated successfully on Patch").toString());
    }
}
