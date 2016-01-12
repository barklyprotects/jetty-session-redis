/**
 * Copyright (C) 2011 Ovea <dev@ovea.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ovea.jetty.session.serializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.ovea.jetty.session.SerializerException;
import org.apache.shiro.subject.SimplePrincipalCollection;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 */
public final class JsonSerializer extends SerializerSkeleton {

    private ObjectMapper mapper;

    @Override
    public void start() {
        mapper = new ObjectMapper();
        mapper.setVisibilityChecker(VisibilityChecker.Std.defaultInstance().withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE));
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, false);
        mapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        mapper = null;
    }

    @Override
    public String serialize(Object o) throws SerializerException {
        try {
            return mapper.writeValueAsString(o);
        } catch (IOException e) {
            throw new SerializerException(e);
        }
    }

    @Override
    public <T> T deserialize(String o, Class<T> targetType) throws SerializerException {
        if (targetType.getName().equals("java.util.Map")) {
            return deserialize(o);
        } else {
            try {
                return mapper.readValue(o,targetType);
            } catch (Exception e) {
                throw new SerializerException(e);
            }
        }
    }

    public <T> T deserialize(String o) throws SerializerException {
        try {
            JsonFactory jsonFactory = new JsonFactory();
            JsonParser parser = jsonFactory.createParser(o);
            parser.setCodec(mapper);
            JsonNode node = parser.readValueAsTree();
            HashMap<String,Object> attributes = new LinkedHashMap<String, Object>();
            processAttributes(attributes,node);
            return (T) attributes;
        } catch (Exception e) {
            throw new SerializerException(e);
        } 
    }

    protected void processAttributes(Map<String, Object> attributes, JsonNode jsonNode) throws JsonProcessingException {
        Iterator<Map.Entry<String, JsonNode>> ite = jsonNode.fields();
        while (ite.hasNext()) {
            Map.Entry<String, JsonNode> entry = ite.next();
            if (entry.getValue().isObject()) {
                if (entry.getKey().equals("org.apache.shiro.subject.support.DefaultSubjectContext_PRINCIPALS_SESSION_KEY")) {
                    SimplePrincipalCollection collection = mapper.treeToValue(entry.getValue(),SimplePrincipalCollection.class);
                    attributes.put(entry.getKey(),collection);
                } else {
                    processAttributes(attributes, entry.getValue());
                }
            } else {
                JsonNode value = entry.getValue();
                switch (value.getNodeType()) {
                    case BOOLEAN:
                        attributes.put(entry.getKey(),value.asBoolean());
                        break;
                    case STRING:
                        attributes.put(entry.getKey(),value.asText());
                        break;
                    case NUMBER:
                        if (value.canConvertToInt()) {
                            attributes.put(entry.getKey(),value.asInt());
                        } else {
                            attributes.put(entry.getKey(),value.asDouble());
                        }
                        break;
                    default:
                        attributes.put(entry.getKey(), entry.getValue().toString());
                }
            }
        }
    }
}
