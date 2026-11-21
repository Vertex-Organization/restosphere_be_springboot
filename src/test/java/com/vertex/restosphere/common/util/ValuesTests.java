package com.vertex.restosphere.common.util;

import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.entity.Bill;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValuesTests {

    @Test
    void codesDifferForUuidV7IdsCreatedInTheSameMinute() {
        var first = new Bill();
        first.setId(UUID.fromString("019a8f3c-1a2b-7c3d-8e4f-5a6b7c8d9e01"));
        var second = new Bill();
        second.setId(UUID.fromString("019a8f3c-1a2b-7f00-9abc-def012345678"));

        assertNotEquals(Values.code("B", first), Values.code("B", second));
        assertEquals("B-7C8D9E01", Values.code("B", first));
    }
}
