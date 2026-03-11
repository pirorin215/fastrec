package com.pirorin215.fastrecmob.service;

import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class DiscoveryGroundingDetailTest {
    @Test
    public void discover() {
        StringBuilder sb = new StringBuilder();
        String[] possibleNames = {
            "com.google.genai.types.GroundingChunkWeb",
            "com.google.genai.types.GroundingChunkRetrievedContext",
            "com.google.genai.types.Web",
            "com.google.genai.types.RetrievedContext",
            "com.google.genai.types.WebSource",
            "com.google.genai.types.GroundingMetadata$Web",
            "com.google.genai.types.GroundingMetadata$RetrievedContext"
        };
        for (String className : possibleNames) {
            try {
                Class<?> clazz = Class.forName(className);
                sb.append("Class: ").append(clazz.getName()).append("\n");
                for (Method m : clazz.getDeclaredMethods()) {
                    sb.append("  ").append(m.getName()).append(" -> ").append(m.getReturnType().getName()).append("\n");
                }
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        throw new AssertionError(sb.toString());
    }
}
