/**
 * 
 */
package com.me.healthplan.utility;

import java.io.IOException;
import java.io.InputStream;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;

/**
 * @author Snehal Patel
 */

@Service
public class JsonValidator {

    public void validateJson(JSONObject object) throws IOException {
        try (InputStream iStream = getClass()
                .getResourceAsStream("/jsonschema.json")) {
            JSONObject rawSchema = new JSONObject(new JSONTokener(iStream));
            Schema schema = SchemaLoader.load(rawSchema);
            schema.validate(object);
        }
    }

}
