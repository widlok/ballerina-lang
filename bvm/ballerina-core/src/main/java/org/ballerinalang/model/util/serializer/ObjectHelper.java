/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.model.util.serializer;

import org.ballerinalang.model.types.BTypes;
import org.ballerinalang.model.values.BRefValueArray;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.util.exceptions.BallerinaException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;

/**
 * Helper methods to operate on Objects.
 *
 * @since 0.98.1
 */
class ObjectHelper {
    private static final String BVALUE_PACKAGE_PATH = getBValuePackagePath();

    private ObjectHelper() {
    }

    /**
     * Get all fields in the class hierarchy until Object class.
     * <p>
     * Skip {@code transient} and compile time constants.
     *
     * @param targetClass from which the fields are inspected.
     * @param depth       depth in the hierarchy; starting class should be 0
     * @return map of Field objects, field names encoded with class hierarchy depth.
     */
    static HashMap<String, Field> getAllFields(Class<?> targetClass, int depth) {
        HashMap<String, Field> fieldMap = new HashMap<>();
        for (Field declaredField : targetClass.getDeclaredFields()) {
            if (skip(declaredField)) {
                continue;
            }
            String name;
            if (depth == 0) {
                name = declaredField.getName();
            } else {
                name = declaredField.getName() + "#" + depth;
            }
            fieldMap.put(name, declaredField);
        }
        if (targetClass != Object.class) {
            fieldMap.putAll(getAllFields(targetClass.getSuperclass(), depth + 1));
        }
        return fieldMap;
    }

    /**
     * Should this field be skipped from serialization process.
     * <p>
     * This is mostly because this field is a transient field or this is a compile time constant field.
     *
     * @param declaredField Field object under consideration.
     * @return whether this field should be skipped or not.
     */
    private static boolean skip(Field declaredField) {
        int modifiers = declaredField.getModifiers();
        if (Modifier.isTransient(modifiers)) {
            // transient fields should not be serialized
            return true;
        }
        return compileTimeConst(modifiers, declaredField.getType());
    }

    private static boolean compileTimeConst(int modifiers, Class<?> type) {
        return Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers) &&
                (type == int.class
                        || type == long.class
                        || type == short.class
                        || type == byte.class
                        || type == char.class
                        || type == float.class
                        || type == double.class
                        || type == String.class);
    }

    /**
     * Set {@code obj} into {@code field} of {@code target} object.
     * <p>
     * This is similar to {@code target.field = obj}.
     *
     * @param target Set the field on this object.
     * @param field  Field instance to be set.
     * @param obj    Set this object to the field.
     */
    static void setField(Object target, Field field, Object obj) {
        primeFinalFieldForAssignment(field);
        try {
            Object newValue = cast(obj, field.getType());
            field.set(target, newValue);
        } catch (IllegalAccessException e) {
            // Ignore it, this is fine.
            // Reason: Either security manager stopping us from setting this field
            // or this is a static final field initialized using compile time constant,
            // we can't assign to them at runtime, nor can we identify them at runtime.
        } catch (IllegalArgumentException e) {
            throw new BallerinaException(e);
        }
    }

    private static void primeFinalFieldForAssignment(Field field) {
        try {
            field.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new BallerinaException(e);
        }
    }

    /**
     * If special method {@code readResolve} is available in this {@code clz} class, invoke it.
     * <p>
     * See java.io.Serializable documentation for more information about 'readResolve'.
     */
    static Object invokeReadResolveOn(Object object, Class<?> clz) {
        try {
            Method readResolved = clz.getDeclaredMethod("readResolve");
            if (readResolved != null) {
                readResolved.setAccessible(true);
                return readResolved.invoke(object);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // no op, will return null from end of this method.
        }
        return null;
    }

    /**
     * Convert to desired type if special conditions are matched.
     * <p>
     * For instance, BValue only support double values, and if the desired type is a single precision float this method
     * will convert BValue double to float.
     *
     * @param obj        object to be converted.
     * @param targetType type that is object will be assigned to.
     * @return converted object.
     */
    static Object cast(Object obj, Class targetType) {
        if (obj == null) {
            return getDefaultValue(targetType);
        }

        Class<?> objClass = obj.getClass();

        // JsonParser always treat float numbers as doubles, if target field is float then cast to float.
        if ((targetType == Float.class || targetType == float.class) && objClass == Double.class) {
            return ((Double) obj).floatValue();
        }

        if (obj instanceof Long) {
            // JsonParser always treat integer numbers as longs, if target field is int then cast to int.
            if ((targetType == Integer.class || targetType == int.class)) {
                return ((Long) obj).intValue();
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return getByte((Long) obj);
            }
            if (targetType == char.class || targetType == Character.class) {
                return getChar((Long) obj);
            }
        }

        return obj;
    }

    /**
     * Get default value for primitive types.
     *
     * @param targetType to be assigned to.
     * @return default value of primitive types and {@code null} for reference types.
     */
    private static Object getDefaultValue(Class targetType) {
        // get default value for primitive type
        if (targetType == int.class
                || targetType == long.class
                || targetType == char.class
                || targetType == short.class
                || targetType == byte.class) {
            return 0;
        } else if (targetType == float.class) {
            return 0.0f;
        } else if (targetType == double.class) {
            return 0.0;
        }
        return null;
    }

    private static byte getByte(Long obj) {
        return obj.byteValue();
    }

    private static char getChar(Long obj) {
        return (char) obj.byteValue();
    }

    static String getTrimmedClassName(Object obj) {
        Class<?> clazz = obj.getClass();
        return getTrimmedClassName(clazz);
    }

    static String getTrimmedClassName(Class<?> clazz) {
        String className = clazz.getName();
        // if obj is a BValue, trim fully qualified name to class name.
        if (BValue.class.isAssignableFrom(clazz) && className.startsWith(BVALUE_PACKAGE_PATH)) {
            className = className.substring(BVALUE_PACKAGE_PATH.length() + 1);
        }
        return className;
    }


    private static String getBValuePackagePath() {
        return BValue.class.getPackage().getName();
    }

    static Class<?> findComponentType(BRefValueArray valueArray, Class<?> targetType) {
        // if target type is a array then find its component type.
        // else (i.e. target type is Object) infer component type from JSON representation.
        if (valueArray.size() > 0 && !targetType.isArray()) {
            if (valueArray.get(0).getType().equals(org.ballerinalang.model.types.BTypes.typeString)) {
                return String.class;
            } else if (valueArray.get(0).getType().equals(org.ballerinalang.model.types.BTypes.typeInt)) {
                return Long.class;
            } else if (valueArray.get(0).getType().equals(BTypes.typeFloat)) {
                return Double.class;
            }
        }
        return targetType.getComponentType();
    }

    static boolean isInstantiable(Class<?> clazz) {
        int modifiers = clazz.getModifiers();
        return !(Modifier.isInterface(modifiers) && Modifier.isAbstract(modifiers));
    }

    static Class<?> findPrimitiveClass(String typeName) {
        switch (typeName) {
            case "byte":
                return byte.class;
            case "char":
                return char.class;
            case "float":
                return float.class;
            case "double":
                return double.class;
            case "short":
                return short.class;
            case "int":
                return int.class;
            case "long":
                return long.class;
            default:
                return null;
        }
    }
}
