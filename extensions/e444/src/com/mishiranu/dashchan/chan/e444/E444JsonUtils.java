package com.mishiranu.dashchan.chan.e444;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.io.IOException;

public final class E444JsonUtils {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

    private E444JsonUtils() {}

    public static <T> T fromJson(String value, Class<T> valueClass) throws IOException {
        return OBJECT_MAPPER.readValue(value, valueClass);
    }

    public static <T> T fromJson(String value, TypeReference<T> valueType) throws IOException {
        return OBJECT_MAPPER.readValue(value, valueType);
    }

    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
