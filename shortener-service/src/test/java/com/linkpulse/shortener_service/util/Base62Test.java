package com.linkpulse.shortener_service.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Base62Test {

    @Test
    void encodesKnownValues() {
        assertEquals("0", Base62.encode(0));
        assertEquals("Z", Base62.encode(61));
        assertEquals("10", Base62.encode(62));
        assertEquals("g8", Base62.encode(1000));
        assertEquals("4c92", Base62.encode(1_000_000));
    }

    @Test
    void decodeReversesEncode() {
        for (long n : new long[]{0, 1, 61, 62, 1000, 123_456_789L, Long.MAX_VALUE}) {
            assertEquals(n, Base62.decode(Base62.encode(n)));
        }
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(-1));
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("ab-c"));
    }
}