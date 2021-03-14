/**
 * 
 */
package com.me.healthplan.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.me.healthplan.dao.HealthPlanDao;

/**
 * @author Snehal Patel
 */

@Service
public class HealthPlanService {

    @Autowired
    HealthPlanDao healthPlanDao;

    public boolean checkIfPlanExists(String key) {
        return healthPlanDao.checkIfPlanExists(key);
    }

    public String savePlanToRedis(JSONObject jsonBody, String planKey) {
        Map<String, Object> healthPlanMap = savePlan(planKey, jsonBody);
        String savedPlan = new JSONObject(healthPlanMap).toString();

        String newEtag = DigestUtils.md5Hex(savedPlan);
        hSet(planKey, "eTag", newEtag);
        return newEtag;
    }

    public Map<String, Object> savePlan(String planKey, JSONObject planObject) {
        convertToMap(planObject);
        Map<String, Object> outputMap = new HashMap<String, Object>();
        getOrDeleteData(planKey, outputMap, false);
        return outputMap;
    }

    private Map<String, Map<String, Object>> convertToMap(
            JSONObject planObject) {

        Map<String, Map<String, Object>> map = new HashMap<String, Map<String, Object>>();
        Map<String, Object> valueMap = new HashMap<String, Object>();

        Iterator<String> iterator = planObject.keySet().iterator();
        while (iterator.hasNext()) {
            System.out.println("Object 1 " + planObject);
            System.out.println("Object 2 " + planObject.get("objectType"));
            String redisKey = planObject.get("objectType") + "_"
                    + planObject.get("objectId");
            String key = iterator.next();
            Object value = planObject.get(key);
            if (value instanceof JSONObject) {
                value = convertToMap((JSONObject) value);
                HashMap<String, Map<String, Object>> val = (HashMap<String, Map<String, Object>>) value;
                healthPlanDao.addSetValue(redisKey + "_" + key,
                        val.entrySet().iterator().next().getKey());
            } else if (value instanceof JSONArray) {
                value = convertToList((JSONArray) value);
                for (HashMap<String, HashMap<String, Object>> entry : (List<HashMap<String, HashMap<String, Object>>>) value) {
                    for (String listKey : entry.keySet()) {
                        healthPlanDao.addSetValue(redisKey + "_" + key,
                                listKey);
                        System.out.println(
                                redisKey + "_" + key + " : " + listKey);
                    }
                }
            } else {
                healthPlanDao.hSet(redisKey, key, value.toString());
                valueMap.put(key, value);
                map.put(redisKey, valueMap);
            }

        }
        System.out.println("MAP: " + map.toString());
        return map;
    }

    private Map<String, Object> getOrDeleteData(String redisKey,
            Map<String, Object> outputMap, boolean isDelete) {

        Set<String> keys = healthPlanDao.getKeys(redisKey + "*");
        for (String key : keys) {
            if (key.equals(redisKey)) {
                if (isDelete) {
                    healthPlanDao.deleteKeys(new String[] { key });
                } else {
                    Map<String, String> val = healthPlanDao
                            .getAllValuesByKey(key);
                    for (String name : val.keySet()) {
                        if (!name.equalsIgnoreCase("eTag")) {
                            outputMap.put(name,
                                    isStringDouble(val.get(name))
                                            ? Double.parseDouble(val.get(name))
                                            : val.get(name));
                        }
                    }
                }

            } else {
                String newStr = key.substring((redisKey + "_").length());
                System.out.println("Key to be searched :" + key
                        + "--------------" + newStr);
                Set<String> members = healthPlanDao.sMembers(key);
                if (members.size() > 1) {
                    List<Object> listObj = new ArrayList<Object>();
                    for (String member : members) {
                        if (isDelete) {
                            getOrDeleteData(member, null, true);
                        } else {
                            Map<String, Object> listMap = new HashMap<String, Object>();
                            listObj.add(
                                    getOrDeleteData(member, listMap, false));

                        }
                    }
                    if (isDelete) {
                        healthPlanDao.deleteKeys(new String[] { key });
                    } else {
                        outputMap.put(newStr, listObj);
                    }

                } else {
                    if (isDelete) {
                        healthPlanDao.deleteKeys(new String[] {
                                members.iterator().next(), key });
                    } else {
                        Map<String, String> val = healthPlanDao
                                .getAllValuesByKey(members.iterator().next());
                        Map<String, Object> newMap = new HashMap<String, Object>();
                        for (String name : val.keySet()) {
                            newMap.put(name,
                                    isStringDouble(val.get(name))
                                            ? Double.parseDouble(val.get(name))
                                            : val.get(name));
                        }
                        outputMap.put(newStr, newMap);
                    }
                }
            }
        }
        return outputMap;

    }

    private List<Object> convertToList(JSONArray array) {
        List<Object> list = new ArrayList<Object>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = convertToList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = convertToMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

    private boolean isStringDouble(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public void hSet(String planKey, String field, String value) {
        healthPlanDao.hSet(planKey, field, value);
    }
    
    public boolean checkIfKeyExists(String planKey){
        return healthPlanDao.checkIfKeyExist(planKey);
    }
    
    public String getEtag(String key, String field) {
        return healthPlanDao.hGet(key, field);
    }
    
    public Map<String, Object> getPlan(String planKey){
        Map<String, Object> outputMap = new HashMap<String, Object>();
        getOrDeleteData(planKey, outputMap, false);
        return outputMap;
    }
    
    public void deletePlan(String planKey) {
        getOrDeleteData(planKey, null, true);
    }
}
