/*
 * Copyright (c) 2014-2018 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.jberet.support.io;

import java.util.Hashtable;
import java.util.StringTokenizer;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.spi.ObjectFactory;

import org.jberet.support._private.SupportMessages;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * An implementation of {@code javax.naming.spi.ObjectFactory} that produces instance of
 * {@code com.fasterxml.jackson.databind.MappingJsonFactory}. This class can be used to create a custom JNDI resource
 * in an application server. See wildfly.home/docs/schema/jboss-as-naming_2_0.xsd for more details.
 *
 * @see "javax.naming.spi.ObjectFactory"
 * @see "wildfly.home/docs/schema/jboss-as-naming_2_0.xsd"
 * @since 1.0.2
 */
public final class MappingJsonFactoryObjectFactory implements ObjectFactory {
    private volatile MappingJsonFactory jsonFactoryCached;

    /**
     * Gets an instance of {@code com.fasterxml.jackson.databind.MappingJsonFactory} based on the resource configuration
     * in the application server. The parameter {@code environment} contains MappingJsonFactory configuration properties,
     * and accepts the following properties:
     * <ul>
     * <li>jsonFactoryFeatures: JsonFactory features as defined in com.fasterxml.jackson.core.JsonFactory.Feature
     * <li>mapperFeatures: ObjectMapper features as defined in com.fasterxml.jackson.databind.MapperFeature
     * <li>deserializationFeatures:
     * <li>serializationFeatures:
     * <li>customDeserializers:
     * <li>customSerializers:
     * <li>deserializationProblemHandlers:
     * <li>customDataTypeModules:
     * <li>inputDecorator: fully-qualified name of a class that extends {@code com.fasterxml.jackson.core.io.InputDecorator}
     * <li>outputDecorator: fully-qualified name of a class that extends {@code com.fasterxml.jackson.core.io.OutputDecorator}
     * </ul>
     *
     * @param obj         the JNDI name of {@code com.fasterxml.jackson.databind.MappingJsonFactory} resource
     * @param name        always null
     * @param nameCtx     always null
     * @param environment a {@code Hashtable} of configuration properties
     * @return an instance of {@code com.fasterxml.jackson.databind.MappingJsonFactory}
     * @throws Exception any exception occurred
     */
    @Override
    public Object getObjectInstance(final Object obj,
                                    final Name name,
                                    final Context nameCtx,
                                    final Hashtable<?, ?> environment) throws Exception {
        MappingJsonFactory jsonFactory = jsonFactoryCached;
        if (jsonFactory == null) {
            synchronized (this) {
                jsonFactory = jsonFactoryCached;
                if (jsonFactory == null) {
                    jsonFactoryCached = jsonFactory = new MappingJsonFactory();
                }

                final ClassLoader classLoader = MappingJsonFactoryObjectFactory.class.getClassLoader();
                final ObjectMapper objectMapper = jsonFactory.getCodec();

                final Object jsonFactoryFeatures = environment.get("jsonFactoryFeatures");
                if (jsonFactoryFeatures != null) {
                    NoMappingJsonFactoryObjectFactory.configureJsonFactoryFeatures(jsonFactory, (String) jsonFactoryFeatures);
                }

                final Object mapperFeatures = environment.get("mapperFeatures");
                if (mapperFeatures != null) {
                    configureMapperFeatures(objectMapper, (String) mapperFeatures);
                }

                final Object deserializationFeatures = environment.get("deserializationFeatures");
                if (deserializationFeatures != null) {
                    configureDeserializationFeatures(objectMapper, (String) deserializationFeatures);
                }

                final Object serializationFeatures = environment.get("serializationFeatures");
                if (serializationFeatures != null) {
                    configureSerializationFeatures(objectMapper, (String) serializationFeatures);
                }

                final Object deserializationProblemHandlers = environment.get("deserializationProblemHandlers");
                if (deserializationProblemHandlers != null) {
                    configureDeserializationProblemHandlers(objectMapper, (String) deserializationProblemHandlers, classLoader);
                }

                configureCustomSerializersAndDeserializers(objectMapper, (String) environment.get("customSerializers"),
                        (String) environment.get("customDeserializers"), (String) environment.get("customDataTypeModules"),
                        classLoader);
                NoMappingJsonFactoryObjectFactory.configureInputDecoratorAndOutputDecorator(jsonFactory, environment);
            }
        }
        return jsonFactory;
    }

    static void configureDeserializationProblemHandlers(final ObjectMapper objectMapper,
                                                        final String deserializationProblemHandlers,
                                                        final ClassLoader classLoader) throws Exception {
        final StringTokenizer st = new StringTokenizer(deserializationProblemHandlers, ", ");
        while (st.hasMoreTokens()) {
            final Class<?> c = classLoader.loadClass(st.nextToken());
            objectMapper.addHandler((DeserializationProblemHandler) c.getDeclaredConstructor().newInstance());
        }
    }

    static void configureCustomSerializersAndDeserializers(final ObjectMapper objectMapper,
                                                           final String customSerializers,
                                                           final String customDeserializers,
                                                           final String customDataTypeModules,
                                                           final ClassLoader classLoader) throws Exception {
        if (customDeserializers != null || customSerializers != null) {
            final SimpleModule simpleModule = new SimpleModule("custom-serializer-deserializer-module");
            if (customSerializers != null) {
                final StringTokenizer st = new StringTokenizer(customSerializers, ", ");
                while (st.hasMoreTokens()) {
                    final Class<?> aClass = classLoader.loadClass(st.nextToken());
                    simpleModule.addSerializer(aClass, (JsonSerializer) aClass.getDeclaredConstructor().newInstance());
                }
            }
            if (customDeserializers != null) {
                final StringTokenizer st = new StringTokenizer(customDeserializers, ", ");
                while (st.hasMoreTokens()) {
                    final Class<?> aClass = classLoader.loadClass(st.nextToken());
                    simpleModule.addDeserializer(aClass, (JsonDeserializer) aClass.getDeclaredConstructor().newInstance());
                }
            }
            objectMapper.registerModule(simpleModule);
        }
        if (customDataTypeModules != null) {
            final StringTokenizer st = new StringTokenizer(customDataTypeModules, ", ");
            while (st.hasMoreTokens()) {
                final Class<?> aClass = classLoader.loadClass(st.nextToken());
                objectMapper.registerModule((Module) aClass.getDeclaredConstructor().newInstance());
            }
        }
    }

    static void configureMapperFeatures(final ObjectMapper objectMapper, final String features) {
        final StringTokenizer st = new StringTokenizer(features, ",");
        while (st.hasMoreTokens()) {
            final String[] pair = NoMappingJsonFactoryObjectFactory.parseSingleFeatureValue(st.nextToken().trim());
            final String key = pair[0];
            final String value = pair[1];

            final MapperFeature feature;
            try {
                feature = MapperFeature.valueOf(key);
            } catch (final Exception e1) {
                throw SupportMessages.MESSAGES.unrecognizedReaderWriterProperty(key, value);
            }
            if ("true".equals(value)) {
                if (!feature.enabledByDefault()) {
                    objectMapper.configure(feature, true);
                }
            } else if ("false".equals(value)) {
                if (feature.enabledByDefault()) {
                    objectMapper.configure(feature, false);
                }
            } else {
                throw SupportMessages.MESSAGES.invalidReaderWriterProperty(null, value, key);
            }
        }
    }

    static void configureSerializationFeatures(final ObjectMapper objectMapper, final String features) {
        final StringTokenizer st = new StringTokenizer(features, ",");
        while (st.hasMoreTokens()) {
            final String[] pair = NoMappingJsonFactoryObjectFactory.parseSingleFeatureValue(st.nextToken().trim());
            final String key = pair[0];
            final String value = pair[1];

            final SerializationFeature feature;
            try {
                feature = SerializationFeature.valueOf(key);
            } catch (final Exception e1) {
                throw SupportMessages.MESSAGES.unrecognizedReaderWriterProperty(key, value);
            }
            if ("true".equals(value)) {
                if (!feature.enabledByDefault()) {
                    objectMapper.configure(feature, true);
                }
            } else if ("false".equals(value)) {
                if (feature.enabledByDefault()) {
                    objectMapper.configure(feature, false);
                }
            } else {
                throw SupportMessages.MESSAGES.invalidReaderWriterProperty(null, value, key);
            }
        }
    }

    static void configureDeserializationFeatures(final ObjectMapper objectMapper, final String features) {
        final StringTokenizer st = new StringTokenizer(features, ",");
        while (st.hasMoreTokens()) {
            final String[] pair = NoMappingJsonFactoryObjectFactory.parseSingleFeatureValue(st.nextToken().trim());
            final String key = pair[0];
            final String value = pair[1];

            final DeserializationFeature feature;
            try {
                feature = DeserializationFeature.valueOf(key);
            } catch (final Exception e1) {
                throw SupportMessages.MESSAGES.unrecognizedReaderWriterProperty(key, value);
            }
            if ("true".equals(value)) {
                if (!feature.enabledByDefault()) {
                    objectMapper.configure(feature, true);
                }
            } else if ("false".equals(value)) {
                if (feature.enabledByDefault()) {
                    objectMapper.configure(feature, false);
                }
            } else {
                throw SupportMessages.MESSAGES.invalidReaderWriterProperty(null, value, key);
            }
        }
    }
}
