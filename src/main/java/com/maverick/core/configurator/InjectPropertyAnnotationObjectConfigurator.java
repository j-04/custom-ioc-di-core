package com.maverick.core.configurator;

import com.maverick.core.api.annotation.CoreConfigurator;
import com.maverick.core.api.annotation.InjectProperty;
import com.maverick.core.api.annotation.Mob;
import com.maverick.core.api.configurator.ObjectConfigurator;
import com.maverick.core.api.context.IApplicationContext;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mob
@CoreConfigurator
public class InjectPropertyAnnotationObjectConfigurator implements ObjectConfigurator {
    private final Map<String, String> properties;

    public InjectPropertyAnnotationObjectConfigurator() {
        URL url = ClassLoader.getSystemClassLoader().getResource("application.properties");
        String path = null;
        if (url != null)
            path = url.getPath();

        Stream<String> lines = null;
        if (path != null) {
            try {
                lines = new BufferedReader(new FileReader(path)).lines();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            if (lines != null)
                this.properties = lines
                        .map(String::strip)
                        .filter(line -> line.matches("^([a-zA-Z0-9]* *= *[\\w\\-()!_ ]*)$"))
                        .map(line -> line.split(" *= *"))
                        .collect(Collectors.toMap(arr -> arr[0], arr -> arr[1]));
            else
                this.properties = Collections.emptyMap();

        } else
            this.properties = Collections.emptyMap();
    }

    @Override
    public void configure(Object o, IApplicationContext context) {
        Objects.requireNonNull(o);
        Objects.requireNonNull(context);

        Class<?> oClass = o.getClass();
        while (!oClass.equals(Object.class)) {
            for (Field field : oClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(InjectProperty.class)) {
                    InjectProperty p = field.getAnnotation(InjectProperty.class);
                    String value = p.value();
                    String property;
                    String propertyName;
                    if (value.isEmpty()) {
                        propertyName = field.getName();
                    } else {
                        propertyName = value;
                    }
                    property = properties.get(propertyName);
                    determineFieldType(o, field, property);
                }
            }
            oClass = oClass.getSuperclass();
        }
    }

    private void determineFieldType(Object object, Field field, String property) {
        Class<?> fieldType = field.getType();
        if (fieldType.equals(Number.class) || fieldType.getSuperclass().equals(Number.class)) {
            injectNumberPropertyInField(object, field, property);
        }
        if (fieldType.equals(Character.class))
            injectCharPropertyInField(object, field, property);
        if (fieldType.equals(String.class))
            injectProperty(object, field, property);
    }

    private void injectCharPropertyInField(Object object, Field field, String property) {
        if (property.length() == 1)
            injectProperty(object, field, property.charAt(0));
        else
            throwRuntimeException(property, field.getType());
    }

    private void injectNumberPropertyInField(Object object, Field field, String property) {
        if (StringUtils.isNumeric(property))
            injectProperty(object, field, Integer.parseInt(property));
        else
            throwRuntimeException(property, field.getType());
    }

    private void injectProperty(Object object, Field field, Object property) {
        try {
            field.setAccessible(true);
            field.set(object, property);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void throwRuntimeException(String property, Class<?> fieldType) {
        throw new RuntimeException(String.format("Can not inject property with value: \"%s\" in field with type: \"%s\"", property, fieldType.getName()));
    }
}
