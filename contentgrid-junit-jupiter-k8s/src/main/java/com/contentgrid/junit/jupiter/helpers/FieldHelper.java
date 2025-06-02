package com.contentgrid.junit.jupiter.helpers;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

@UtilityClass
public class FieldHelper {

    public static List<Field> findFields(ExtensionContext context, Class<?> fieldType, Predicate<Field> predicate) {
        return ReflectionSupport.findFields(
                context.getRequiredTestClass(),
                field -> predicate.test(field) && fieldType.equals(field.getType()),
                HierarchyTraversalMode.TOP_DOWN);
    }

    public static Object getFieldValue(Field field, Object entity) throws IllegalAccessException {
        var isAccessible = field.isAccessible();
        try {
            field.setAccessible(true);
            return field.get(entity);
        } finally {
            field.setAccessible(isAccessible);
        }
    }

    public static void setFieldValue(Field field, Object entity, Object value) throws IllegalAccessException {
        final boolean isAccessible = field.isAccessible();
        field.setAccessible(true);
        field.set(entity, value);
        field.setAccessible(isAccessible);
    }
}
