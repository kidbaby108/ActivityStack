/*
 *    Copyright Aesean
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.aesean.activitystack.utils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * ReflectUtils
 * 一个简单的反射工具类。可以帮助你像写脚本语言一样调用处理java类。
 *
 * @author xl
 * @version V0.1
 * @since 09/01/2017
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class ReflectUtils {
    private static Object getField(Object target, String arg) throws Exception {
        if (arg.contains(".")) {
            throw new IllegalArgumentException("属性名称不能包含：.");
        }
        Class<?> clazz = target.getClass();
        Field field = clazz.getDeclaredField(arg);
        if (!field.isAccessible()) {
            field.setAccessible(true);
        }
        return field.get(target);
    }

    public static Method reflectMethod(Class<?> clazz, String methodName
            , Class<?>... parameterTypes) throws Exception {
        try {
            return clazz.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass == null) {
                throw e;
            }
            return reflectMethod(superclass, methodName);
        }
    }

    public static Field reflectField(Class<?> clazz, String fieldName) throws Exception {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass == null) {
                throw e;
            }
            return reflectField(superclass, fieldName);
        }
    }

    private static boolean isMethod(String block) {
        return block.contains("(") && block.endsWith(")");
    }

    public static Object reflect(Object target, String script) throws Exception {
        return reflect(target, script, null);
    }

    public static Object reflect(Object target, String script, Object[] args) throws Exception {
        return reflect(target, script, args, null);
    }

    public static Object reflect(Object target, String script, Object[] args
            , Object newValue) throws Exception {
        script = script.trim();
        Class[] classes = null;
        if (args != null) {
            classes = new Class[args.length];
            for (int i = 0; i < classes.length; i++) {
                classes[i] = args[i].getClass();
            }
        }
        return reflect(target, script, args, classes, newValue, true);
    }

    private static String getMethodName(String block) {
        // 当block是method的时候block可能是以下几种类型
        // method()
        // method(%1)
        // method(%1,%2)
        return block.substring(0, block.indexOf('('));
    }

    public static Object reflect(Object target, String script, final Object[] args
            , final Class[] classes) throws Exception {
        return reflect(target, script, args, classes, null, false);
    }

    public static Object reflect(Object target, String script, final Object[] args
            , final Class[] classes, final Object newValue) throws Exception {
        return reflect(target, script, args, classes, newValue, true);
    }

    /**
     * 所有重载方法最终都会进入这个方法。注意，这个方法没有回滚机制，会把.分隔成一组block，
     * 然后按从左到右的顺序一个个处理。不支持嵌套，不支持调用静态方法。
     * 仅支持类似调用：
     * ReflectUtils.reflect(application, "mLoadedApk.mActivityThread.mActivities");
     * ReflectUtils.reflect(application, "mModel.getName().toString()");
     * ReflectUtils.reflect(application, "mModel.setName(%1)");
     *
     * @param target      需要反射的对象
     * @param script      脚本
     * @param args        实际的参数对象
     * @param classes     参数对象的类类型
     * @param newValue    如果脚本最后一个block是属性，则会尝试把这个对象赋给反射到的对象
     * @param setNewValue 是否需要设置新对象，这里不能通过newValue是否为null判断是否需要设置新数值，
     *                    因为可能用户就是需要把目标设置为null
     * @return 最终结果。注意，如果脚本最后一个block是属性，无论是否需要更新新属性，这里都会返回反射到的对象。
     * 如果最后一个block是方法，这里会返回方法的处理结果。
     * @throws Exception 强制处理异常
     */
    private static Object reflect(Object target, String script, final Object[] args
            , final Class[] classes, final Object newValue, boolean setNewValue) throws Exception {
        if (script.startsWith(".")) {
            script = script.substring(1, script.length());
        }
        int firstPointIndex = script.indexOf('.');
        final String block;
        if (firstPointIndex == -1) {
            // 不包含点，则block=字符串本身
            block = script;
        } else {
            // 拿到第一个需要处理的block
            block = script.substring(0, firstPointIndex);
        }
        // 生成新的script
        final String newScript;
        // ReflectUtils.ReflectException.getInstance();
        final Class<?> targetClass;
        if (target == null) {
            targetClass = Class.forName(block);
            String sub = script.substring(firstPointIndex + 1, script.length());
            newScript = sub.substring(sub.indexOf('.') + 1, sub.length());
        } else {
            targetClass = target.getClass();
            newScript = script.substring(firstPointIndex + 1, script.length());
        }
        if (isMethod(block)) {
            String methodName = getMethodName(block);
            final Class<?>[] parameterTypes;
            final Object[] arguments;

            int leftIndex = block.indexOf('(') + 1;
            int rightIndex = block.indexOf(')');
            if (leftIndex == rightIndex) {
                // 没有参数
                parameterTypes = null;
                arguments = null;
            } else {
                String paramStr = block.substring(leftIndex, rightIndex);
                String[] split = paramStr.split(",");
                parameterTypes = new Class[split.length];
                arguments = new Object[split.length];
                for (int i = 0; i < split.length; i++) {
                    String substring = split[i].substring(split[i].indexOf('%') + 1
                            , split[i].length());
                    int index = Integer.parseInt(substring) - 1;
                    parameterTypes[i] = classes[index];
                    arguments[i] = args[index];
                }
            }

            try {
                Method method = reflectMethod(targetClass, methodName, parameterTypes);
                Object invoke = method.invoke(target, arguments);
                if (firstPointIndex == -1) {
                    return invoke;
                }
                return reflect(invoke, newScript, args, classes, newValue);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("无法在类：" + targetClass
                        + "及其父类，找到方法：" + methodName
                        + "。请检查脚本中的：" + script);
            } catch (InvocationTargetException e) {
                throw new RuntimeException("在类：" + targetClass
                        + "执行方法：" + methodName
                        + "发生异常。请检查脚本中的：" + script);
            }
        } else {
            Field field;
            try {
                field = reflectField(targetClass, block);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("无法在类：" + targetClass
                        + "及其父类，找到属性：" + block
                        + "。请检查脚本中的：" + script);
            }
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            Object o = field.get(target);
            if (firstPointIndex == -1) {
                if (setNewValue) {
                    field.set(target, newValue);
                }
                return o;
            }
            return reflect(o, newScript, args, classes);
        }
    }

    public static Object get(Object target, String arg) throws Exception {
        int firstPointIndex = arg.indexOf('.');
        final String fieldName;
        if (firstPointIndex != -1) {
            fieldName = arg.substring(0, firstPointIndex);
        } else {
            fieldName = arg;
            return getField(target, fieldName);
        }
        Class<?> clazz = target.getClass();
        Field field = clazz.getDeclaredField(fieldName);
        if (!field.isAccessible()) {
            field.setAccessible(true);
        }
        Object result = field.get(target);
        return get(result, arg.substring(firstPointIndex + 1, arg.length()));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        if (fieldName.contains(".")) {
            throw new IllegalArgumentException("属性名称不能包含：.");
        }
        Class<?> clazz = target.getClass();
        Field field = clazz.getDeclaredField(fieldName);
        if (!field.isAccessible()) {
            field.setAccessible(true);
        }
        field.set(target, value);
    }

    public static void set(Object target, String arg, Object value) throws Exception {
        int firstIndexOf = arg.indexOf('.');
        if (firstIndexOf != -1) {
            String substring = arg.substring(0, firstIndexOf);
            Class<?> clazz = target.getClass();
            Field field = clazz.getDeclaredField(substring);
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            Object result = field.get(target);
            set(result, arg.substring(firstIndexOf + 1, arg.length()), value);
        } else {
            setField(target, arg, value);
        }
    }
//
//    public static class ReflectException extends Exception {
//
//        private static final long serialVersionUID = -2504804697196582566L;
//
//        /**
//         * Constructs a <code>ReflectException</code> without a detail message.
//         */
//        public ReflectException() {
//            super();
//        }
//
//        /**
//         * Constructs a <code>ReflectException</code> with a detail message.
//         *
//         * @param s the detail message.
//         */
//        public ReflectException(String s) {
//            super(s);
//        }
//    }
}