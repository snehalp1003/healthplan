/**
 * 
 */
package com.me.healthplan.dao;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Repository;

import redis.clients.jedis.Jedis;

/**
 * @author Snehal Patel
 */
@Repository
public class HealthPlanDao {

    public boolean checkIfPlanExists(String key) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.exists(key);
        }
    }

    // Adds specified member to the set value stored at a specified key
    public void addSetValue(String key, String value) {
        try (Jedis jedis = new Jedis("localhost")) {
            jedis.sadd(key, value);
        }
    }

    public String hGet(String key, String field) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.hget(key, field);
        }
    }

    // Sets the specified hash field to the specified value
    public void hSet(String key, String field, String value) {
        try (Jedis jedis = new Jedis("localhost")) {
            jedis.hset(key, field, value);
        }
    }

    // Get all keys that matches the pattern
    public Set<String> getKeys(String pattern) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.keys(pattern);
        }
    }

    // Get all fields and associated values in a hash
    public Map<String, String> getAllValuesByKey(String key) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.hgetAll(key);
        }
    }

    // Return all members of the set value stored at the specified key
    public Set<String> sMembers(String key) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.smembers(key);
        }
    }
    
    public boolean checkIfKeyExist(String key) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.exists(key);
        }
    }

    // Delete keys
    public long deleteKeys(String[] keys) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.del(keys);
        }
    }
}
