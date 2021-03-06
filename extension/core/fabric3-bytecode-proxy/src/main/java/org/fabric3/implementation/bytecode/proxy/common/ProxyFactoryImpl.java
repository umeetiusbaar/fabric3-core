/*
 * Fabric3
 * Copyright (c) 2009-2015 Metaform Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 */
package org.fabric3.implementation.bytecode.proxy.common;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fabric3.api.host.Fabric3Exception;
import org.fabric3.spi.classloader.BytecodeClassLoader;
import org.fabric3.spi.classloader.ClassLoaderRegistry;
import org.fabric3.spi.contribution.Contribution;
import org.fabric3.spi.contribution.ContributionServiceListener;
import org.oasisopen.sca.annotation.Reference;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;

/**
 * Implementation that uses ASM for bytecode generation.
 */
public class ProxyFactoryImpl implements ProxyFactory, ContributionServiceListener {
    private ClassLoaderRegistry classLoaderRegistry;

    private Map<URI, BytecodeClassLoader> classLoaderCache = new HashMap<>();

    public ProxyFactoryImpl(@Reference ClassLoaderRegistry classLoaderRegistry) {
        this.classLoaderRegistry = classLoaderRegistry;
    }

    public <T> T createProxy(URI classLoaderKey, Class<T> interfaze, Method[] methods, Class<? extends ProxyDispatcher> dispatcher, boolean wrapped)
            throws Fabric3Exception {
        if (wrapped) {
            return createWrappedProxy(classLoaderKey, interfaze, methods, dispatcher);
        } else {
            return createUnWrappedProxy(classLoaderKey, interfaze, methods, dispatcher);
        }
    }

    public void onUninstall(Contribution contribution) {
        // remove cached classloader for the contribution on undeploy
        classLoaderCache.remove(contribution.getUri());
    }

    /**
     * Creates a proxy that wraps parameters in an object array like JDK proxies when invoking a {@link ProxyDispatcher}.
     *
     * @param classLoaderKey the key for the classloader that the proxy interface is to be loaded in
     * @param interfaze      the proxy interface
     * @param methods        the proxy methods
     * @param dispatcher     the dispatcher
     * @return the proxy
     * @throws Fabric3Exception if there is an error creating the proxy
     */
    @SuppressWarnings("unchecked")
    private <T> T createWrappedProxy(URI classLoaderKey, Class<T> interfaze, Method[] methods, Class<? extends ProxyDispatcher> dispatcher)
            throws Fabric3Exception {

        String className = interfaze.getName() + "_Proxy_" + dispatcher.getSimpleName();  // ensure multiple dispatchers can be defined for the same interface

        // check if the proxy class has already been created
        BytecodeClassLoader generationLoader = getClassLoader(classLoaderKey);
        try {
            Class<T> proxyClass = (Class<T>) generationLoader.loadClass(className);
            return proxyClass.newInstance();
        } catch (ClassNotFoundException e) {
            // ignore
        } catch (InstantiationException | IllegalAccessException e) {
            throw new Fabric3Exception(e);
        }

        String interfazeName = Type.getInternalName(interfaze);
        String handlerName = Type.getInternalName(dispatcher);
        String handlerDescriptor = Type.getDescriptor(dispatcher);
        String classNameInternal = Type.getInternalName(interfaze) + "_Proxy_" + dispatcher.getSimpleName();

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(Opcodes.V1_7, ACC_PUBLIC + ACC_SUPER, classNameInternal, null, handlerName, new String[]{interfazeName});

        cw.visitSource(interfaze.getName() + "Proxy.java", null);

        // write the ctor
        writeConstructor(handlerName, handlerDescriptor, cw);

        // write the methods
        int methodIndex = 0;
        for (Method method : methods) {
            String methodSignature = Type.getMethodDescriptor(method);
            String[] exceptions = new String[method.getExceptionTypes().length];
            for (int i = 0; i < exceptions.length; i++) {
                exceptions[i] = Type.getInternalName(method.getExceptionTypes()[i]);
            }
            mv = cw.visitMethod(ACC_PUBLIC, method.getName(), methodSignature, null, exceptions);
            mv.visitCode();

            List<Label> exceptionLabels = new ArrayList<>();
            Label label2 = new Label();
            Label label3 = new Label();

            for (String exception : exceptions) {
                Label endLabel = new Label();
                exceptionLabels.add(endLabel);
                mv.visitTryCatchBlock(label2, label3, endLabel, exception);

            }

            mv.visitLabel(label2);
            mv.visitVarInsn(ALOAD, 0);

            // set the method index used to dispatch on
            if (methodIndex >= 0 && methodIndex <= 5) {
                // use an integer constant if within range
                mv.visitInsn(Opcodes.ICONST_0 + methodIndex);
            } else {
                mv.visitIntInsn(Opcodes.BIPUSH, methodIndex);
            }
            methodIndex++;

            int numberOfParameters = method.getParameterTypes().length;

            int index = 0;
            int stack = 1;
            if (numberOfParameters == 0) {
                // no params, load null
                mv.visitInsn(Opcodes.ACONST_NULL);

            } else {
                // create the Object[] used to pass the parameters to _f3_invoke and push it on the stack
                if (numberOfParameters >= 0 && numberOfParameters <= 5) {
                    // use an integer constant if within range
                    mv.visitInsn(Opcodes.ICONST_0 + numberOfParameters);
                } else {
                    mv.visitIntInsn(Opcodes.BIPUSH, numberOfParameters);
                }
                mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                mv.visitInsn(DUP);

                for (Class<?> param : method.getParameterTypes()) {
                    if (Integer.TYPE.equals(param)) {
                        mv.visitInsn(Opcodes.ICONST_0 + index);
                        mv.visitVarInsn(Opcodes.ILOAD, stack);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
                        mv.visitInsn(AASTORE);
                        if (index < numberOfParameters - 1) {
                            mv.visitInsn(DUP);
                        }
                    } else if (Float.TYPE.equals(param)) {
                        mv.visitInsn(Opcodes.ICONST_0 + index);
                        mv.visitVarInsn(Opcodes.FLOAD, stack);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
                        mv.visitInsn(AASTORE);
                        if (index < numberOfParameters - 1) {
                            mv.visitInsn(DUP);
                        }
                    } else if (Boolean.TYPE.equals(param)) {
                        mv.visitInsn(Opcodes.ICONST_0 + index);
                        mv.visitVarInsn(Opcodes.ILOAD, stack);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
                        mv.visitInsn(AASTORE);
                        if (index < numberOfParameters - 1) {
                            mv.visitInsn(DUP);
                        }
                    } else if (Short.TYPE.equals(param)) {
                        mv.visitInsn(Opcodes.ICONST_0 + index);
                        mv.visitVarInsn(Opcodes.ILOAD, stack);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;");
                        mv.visitInsn(AASTORE);
                        if (index < numberOfParameters - 1) {
                            mv.visitInsn(DUP);
                        }
                    } else if (Byte.TYPE.equals(param)) {
                        mv.visitInsn(Opcodes.ICONST_0 + index);
                        mv.visitVarInsn(Opcodes.ILOAD, stack);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;");
                        mv.visitInsn(AASTORE);
                        if (index < numberOfParameters - 1) {
                            mv.visitInsn(DUP);
                        }
                    } else if (Double.TYPE.equals(param)) {
                        mv.visitInsn(Opcodes.ICONST_0 + index);
                        mv.visitVarInsn(Opcodes.DLOAD, stack);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
                        mv.visitInsn(AASTORE);
                        if (index < numberOfParameters - 1) {
                            mv.visitInsn(DUP);
                        }
                        stack++;   // double occupies two positions

                    } else if (Long.TYPE.equals(param)) {
                        mv.visitInsn(Opcodes.ICONST_0 + index);
                        mv.visitVarInsn(Opcodes.LLOAD, stack);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
                        mv.visitInsn(AASTORE);
                        if (index < numberOfParameters - 1) {
                            mv.visitInsn(DUP);
                        }
                        stack++;   // long occupies two positions
                    } else {
                        // object type
                        mv.visitInsn(Opcodes.ICONST_0 + index);
                        mv.visitVarInsn(ALOAD, stack);
                        mv.visitInsn(AASTORE);
                        if (index < numberOfParameters - 1) {
                            mv.visitInsn(DUP);
                        }
                    }
                    index++;
                }
                // TODO other primitive types
                stack++;
            }

            mv.visitMethodInsn(INVOKEVIRTUAL, classNameInternal, "_f3_invoke", "(ILjava/lang/Object;)Ljava/lang/Object;");

            // handle return values
            writeReturn(method, label3, mv);

            // implement catch blocks
            index = 0;
            for (String exception : exceptions) {
                Label endLabel = exceptionLabels.get(index);
                mv.visitLabel(endLabel);
                mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{exception});
                mv.visitVarInsn(ASTORE, stack);
                Label label6 = new Label();
                mv.visitLabel(label6);
                mv.visitVarInsn(ALOAD, stack);
                mv.visitInsn(ATHROW);
                index++;
            }

            Label label7 = new Label();
            mv.visitLabel(label7);
            mv.visitMaxs(7, 5);
            mv.visitEnd();
        }

        cw.visitEnd();

        byte[] data = cw.toByteArray();
        Class<?> proxyClass = generationLoader.defineClass(className, data);
        try {
            return (T) proxyClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new Fabric3Exception(e);
        }
    }

    /**
     * Creates a proxy that sends a single parameter to a {@link ProxyDispatcher}.
     *
     * @param classLoaderKey the key for the classloader that the proxy interface is to be loaded in
     * @param interfaze      the proxy interface
     * @param methods        the proxy methods
     * @param dispatcher     the dispatcher
     * @return the proxy
     * @throws Fabric3Exception if there is an error creating the proxy
     */
    @SuppressWarnings("unchecked")
    private <T> T createUnWrappedProxy(URI classLoaderKey, Class<T> interfaze, Method[] methods, Class<? extends ProxyDispatcher> dispatcher)
            throws Fabric3Exception {

        String className = interfaze.getName() + "_Proxy_" + dispatcher.getSimpleName();  // ensure multiple dispatchers can be defined for the same interface

        // check if the proxy class has already been created
        BytecodeClassLoader generationLoader = getClassLoader(classLoaderKey);
        try {
            Class<T> proxyClass = (Class<T>) generationLoader.loadClass(className);
            return proxyClass.newInstance();
        } catch (ClassNotFoundException e) {
            // ignore
        } catch (InstantiationException | IllegalAccessException e) {
            throw new Fabric3Exception(e);
        }

        String interfazeName = Type.getInternalName(interfaze);
        String handlerName = Type.getInternalName(dispatcher);
        String handlerDescriptor = Type.getDescriptor(dispatcher);
        String classNameInternal = Type.getInternalName(interfaze) + "_Proxy_" + dispatcher.getSimpleName();

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(Opcodes.V1_7, ACC_PUBLIC + ACC_SUPER, classNameInternal, null, handlerName, new String[]{interfazeName});

        cw.visitSource(interfaze.getName() + "Proxy.java", null);

        // write the ctor
        writeConstructor(handlerName, handlerDescriptor, cw);

        // write the methods
        int methodIndex = 0;
        for (Method method : methods) {
            String methodSignature = Type.getMethodDescriptor(method);
            String[] exceptions = new String[method.getExceptionTypes().length];
            for (int i = 0; i < exceptions.length; i++) {
                exceptions[i] = Type.getInternalName(method.getExceptionTypes()[i]);
            }
            mv = cw.visitMethod(ACC_PUBLIC, method.getName(), methodSignature, null, exceptions);
            mv.visitCode();

            List<Label> exceptionLabels = new ArrayList<>();
            Label label2 = new Label();
            Label label3 = new Label();

            for (String exception : exceptions) {
                Label endLabel = new Label();
                exceptionLabels.add(endLabel);
                mv.visitTryCatchBlock(label2, label3, endLabel, exception);

            }

            mv.visitLabel(label2);
            mv.visitVarInsn(ALOAD, 0);

            // set the method index used to dispatch on
            if (methodIndex >= 0 && methodIndex <= 5) {
                // use an integer constant if within range
                mv.visitInsn(Opcodes.ICONST_0 + methodIndex);
            } else {
                mv.visitIntInsn(Opcodes.BIPUSH, methodIndex);
            }
            methodIndex++;

            int numberOfParameters = method.getParameterTypes().length;
            if (numberOfParameters > 1) {
                // FIXME
                throw new AssertionError("Not supported");
            }
            if (numberOfParameters == 0) {
                // no params, load null
                mv.visitInsn(Opcodes.ACONST_NULL);

            } else {

                Class<?> param = method.getParameterTypes()[0];
                if (Integer.TYPE.equals(param)) {
                    mv.visitVarInsn(ILOAD, 1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
                } else if (Float.TYPE.equals(param)) {
                    mv.visitVarInsn(Opcodes.FLOAD, 1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
                } else if (Boolean.TYPE.equals(param)) {
                    mv.visitVarInsn(Opcodes.ILOAD, 1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
                } else if (Short.TYPE.equals(param)) {
                    mv.visitVarInsn(Opcodes.ILOAD, 1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;");
                } else if (Byte.TYPE.equals(param)) {
                    mv.visitVarInsn(Opcodes.ILOAD, 1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;");
                } else if (Double.TYPE.equals(param)) {
                    mv.visitVarInsn(Opcodes.DLOAD, 1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
                } else if (Long.TYPE.equals(param)) {
                    mv.visitVarInsn(Opcodes.LLOAD, 1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
                } else {
                    // object type
                    mv.visitVarInsn(ALOAD, 1);
                }
            }

            mv.visitMethodInsn(INVOKEVIRTUAL, classNameInternal, "_f3_invoke", "(ILjava/lang/Object;)Ljava/lang/Object;");

            // handle return values
            writeReturn(method, label3, mv);

            // implement catch blocks
            int index = 0;
            for (String exception : exceptions) {
                Label endLabel = exceptionLabels.get(index);
                mv.visitLabel(endLabel);
                mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{exception});
                mv.visitVarInsn(ASTORE, 1);
                Label label6 = new Label();
                mv.visitLabel(label6);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitInsn(ATHROW);
                index++;
            }

            Label label7 = new Label();
            mv.visitLabel(label7);
            mv.visitMaxs(7, 5);
            mv.visitEnd();
        }

        cw.visitEnd();

        byte[] data = cw.toByteArray();
        Class<?> proxyClass = generationLoader.defineClass(className, data);
        try {
            return (T) proxyClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new Fabric3Exception(e);
        }
    }

    private void writeConstructor(String handlerName, String handlerDescriptor, ClassWriter cw) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, handlerName, "<init>", "()V");
        mv.visitInsn(RETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLocalVariable("this", handlerDescriptor, null, l0, l1, 0);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private void writeReturn(Method method, Label endLabel, MethodVisitor mv) {
        Class<?> returnType = method.getReturnType();

        if (Void.TYPE.equals(returnType)) {
            mv.visitInsn(Opcodes.POP);
            mv.visitLabel(endLabel);
            mv.visitInsn(RETURN);
        } else if (returnType.isPrimitive()) {
            if (Double.TYPE.equals(returnType)) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D");
                mv.visitLabel(endLabel);
                mv.visitInsn(Opcodes.DRETURN);
            } else if (Long.TYPE.equals(returnType)) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J");
                mv.visitLabel(endLabel);
                mv.visitInsn(Opcodes.LRETURN);
            } else if (Integer.TYPE.equals(returnType)) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I");
                mv.visitLabel(endLabel);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (Float.TYPE.equals(returnType)) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F");
                mv.visitLabel(endLabel);
                mv.visitInsn(Opcodes.FRETURN);
            } else if (Short.TYPE.equals(returnType)) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S");
                mv.visitLabel(endLabel);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (Byte.TYPE.equals(returnType)) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B");
                mv.visitLabel(endLabel);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (Boolean.TYPE.equals(returnType)) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z");
                mv.visitLabel(endLabel);
                mv.visitInsn(Opcodes.IRETURN);
            }
        } else {
            String internalTypeName = Type.getInternalName(returnType);
            mv.visitTypeInsn(CHECKCAST, internalTypeName);
            mv.visitLabel(endLabel);
            mv.visitInsn(ARETURN);
        }
    }

    /**
     * Returns a classloader for loading the proxy class, creating one if necessary.
     *
     * @param classLoaderKey the key of the contribution classloader the proxy is being loaded for; set as the parent
     * @return the classloader
     */
    private BytecodeClassLoader getClassLoader(URI classLoaderKey) {
        ClassLoader parent = classLoaderRegistry.getClassLoader(classLoaderKey);
        BytecodeClassLoader generationClassLoader = classLoaderCache.get(classLoaderKey);
        if (generationClassLoader == null) {
            generationClassLoader = new BytecodeClassLoader(classLoaderKey, parent);
            generationClassLoader.addParent(getClass().getClassLoader()); // proxy classes need to be visible as well
            classLoaderCache.put(classLoaderKey, generationClassLoader);
        }
        return generationClassLoader;
    }

    public void onStore(Contribution contribution) {

    }

    public void onProcessManifest(Contribution contribution) {

    }

    public void onInstall(Contribution contribution) {

    }

    public void onUpdate(Contribution contribution) {

    }

    public void onRemove(Contribution contribution) {

    }
}
