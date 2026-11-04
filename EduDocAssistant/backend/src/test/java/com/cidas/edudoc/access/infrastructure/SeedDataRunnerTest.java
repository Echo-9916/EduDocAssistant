package com.cidas.edudoc.access.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeedDataRunnerTest {

    private static com.cidas.edudoc.ai.infrastructure.AiModelProperties models() {
        return new com.cidas.edudoc.ai.infrastructure.AiModelProperties(
                new com.cidas.edudoc.ai.infrastructure.AiModelProperties.Llm("Kimi-K2", 0.3),
                new com.cidas.edudoc.ai.infrastructure.AiModelProperties.Embedding("bge-m3", 1024),
                new com.cidas.edudoc.ai.infrastructure.AiModelProperties.Rerank("bge-reranker-v2-m3"));
    }

    @Test
    void seedsNewAccountWithEncodedPasswordAndRole() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("Cidas@123")).thenReturn("HASH");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("student"))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("student"))).thenReturn("HEX1");

        new SeedDataRunner(jdbc, encoder, models()).seed(List.of(
                new SeedDataRunner.SeedAccount("student", "学生测试", "STUDENT", "A", "Cidas@123")));

        verify(jdbc).update(startsWith("INSERT INTO edudoc_user"), eq("student"), eq("学生测试"), eq("HASH"), eq("A"));
        verify(jdbc).update(startsWith("INSERT INTO edudoc_user_role"), eq("HEX1"), eq("STUDENT"));
    }

    @Test
    void seedsNewCourseOnceAndSkipsExisting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SeedDataRunner runner = new SeedDataRunner(jdbc, mock(PasswordEncoder.class), models());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("CS101"), eq("2026-FALL"))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("MATH201"), eq("2026-FALL"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("teacher"))).thenReturn("HT");
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("CS101"), eq("2026-FALL"))).thenReturn("HC");

        runner.seedCourses();

        verify(jdbc).update(startsWith("INSERT INTO edudoc_course ("),
                eq("CS101"), eq("计算机导论"), eq("2026-FALL"), eq("HT"));
        verify(jdbc).update(startsWith("INSERT INTO edudoc_course_member"), eq("HC"), eq("TEACHER"), eq("teacher"));
        verify(jdbc).update(startsWith("INSERT INTO edudoc_course_member"), eq("HC"), eq("STUDENT"), eq("student"));
        verify(jdbc, never()).update(startsWith("INSERT INTO edudoc_course ("),
                eq("MATH201"), any(), any(), any());
    }

    @Test
    void skipsExistingAccount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("student"))).thenReturn(1);

        new SeedDataRunner(jdbc, encoder, models()).seed(List.of(
                new SeedDataRunner.SeedAccount("student", "学生测试", "STUDENT", "A", "Cidas@123")));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(encoder, never()).encode(anyString());
    }
}
