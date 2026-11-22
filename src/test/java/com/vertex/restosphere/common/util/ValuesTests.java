package com.vertex.restosphere.common.util;

import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.entity.Bill;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValuesTests {

    @Test
    void phoneNormalizesVietnameseNumbersAndPreservesInternationalNumbers() {
        assertEquals("0706710269", Values.phone(Values.map("phone", "+84706710269")));
        assertEquals("0706710269", Values.phone(Values.map("phone", "+84 (706) 710-269")));
        assertEquals("0706710269", Values.phone(Values.map("phone", "070.671.0269")));
        assertEquals("+14155552671", Values.phone(Values.map("phone", "+1 (415) 555-2671")));
        assertEquals("00447012345678", Values.phone(Values.map("phone", "00447012345678")));
    }

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
