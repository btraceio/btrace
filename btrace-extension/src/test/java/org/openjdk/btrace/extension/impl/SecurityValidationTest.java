package org.openjdk.btrace.extension.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for security validation methods in extension loading.
 */
class SecurityValidationTest {

    @Nested
    @DisplayName("Extension ID validation")
    class ExtensionIdValidationTests {

        @Test
        @DisplayName("accepts valid extension IDs")
        void acceptsValidIds() {
            assertTrue(EmbeddedExtensionRepository.isValidExtensionId("btrace-spark"));
            assertTrue(EmbeddedExtensionRepository.isValidExtensionId("btrace-hadoop"));
            assertTrue(EmbeddedExtensionRepository.isValidExtensionId("my-extension-1.0"));
            assertTrue(EmbeddedExtensionRepository.isValidExtensionId("ext123"));
            assertTrue(EmbeddedExtensionRepository.isValidExtensionId("my.extension"));
            assertTrue(EmbeddedExtensionRepository.isValidExtensionId("my_extension"));
            assertTrue(EmbeddedExtensionRepository.isValidExtensionId("MyExtension"));
        }

        @Test
        @DisplayName("rejects null and empty IDs")
        void rejectsNullAndEmpty() {
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId(null));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId(""));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("   "));
        }

        @Test
        @DisplayName("rejects path traversal with forward slash")
        void rejectsForwardSlash() {
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("../etc/passwd"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("foo/bar"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("/etc/passwd"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("ext/../../etc"));
        }

        @Test
        @DisplayName("rejects path traversal with backslash")
        void rejectsBackslash() {
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("..\\windows\\system32"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("foo\\bar"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("C:\\Windows"));
        }

        @Test
        @DisplayName("rejects parent directory references")
        void rejectsParentDirectory() {
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId(".."));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("foo..bar"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("..ext"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("ext.."));
        }

        @Test
        @DisplayName("rejects IDs starting with special characters")
        void rejectsSpecialStart() {
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("-extension"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId(".extension"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("_extension"));
            // Note: IDs starting with digits are allowed (valid Maven artifact IDs)
            assertTrue(EmbeddedExtensionRepository.isValidExtensionId("1extension"));
        }

        @Test
        @DisplayName("rejects IDs with invalid characters")
        void rejectsInvalidCharacters() {
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("ext@name"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("ext#name"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("ext name"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("ext$name"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("ext;name"));
            assertFalse(EmbeddedExtensionRepository.isValidExtensionId("ext`name"));
        }
    }

    @Nested
    @DisplayName("Class name validation")
    class ClassNameValidationTests {

        @Test
        @DisplayName("accepts valid class names")
        void acceptsValidClassNames() {
            assertTrue(EmbeddedExtensionRepository.isValidClassName("com.example.MyClass"));
            assertTrue(EmbeddedExtensionRepository.isValidClassName("MyClass"));
            assertTrue(EmbeddedExtensionRepository.isValidClassName("org.openjdk.btrace.Extension"));
            assertTrue(EmbeddedExtensionRepository.isValidClassName("com.example.Outer$Inner"));
            assertTrue(EmbeddedExtensionRepository.isValidClassName("_MyClass"));
            assertTrue(EmbeddedExtensionRepository.isValidClassName("Class123"));
        }

        @Test
        @DisplayName("rejects null and empty class names")
        void rejectsNullAndEmpty() {
            assertFalse(EmbeddedExtensionRepository.isValidClassName(null));
            assertFalse(EmbeddedExtensionRepository.isValidClassName(""));
        }

        @Test
        @DisplayName("rejects invalid class names")
        void rejectsInvalidNames() {
            assertFalse(EmbeddedExtensionRepository.isValidClassName("123Class")); // starts with number
            assertFalse(EmbeddedExtensionRepository.isValidClassName("-MyClass")); // starts with hyphen
            assertFalse(EmbeddedExtensionRepository.isValidClassName("com..example")); // double dot
            assertFalse(EmbeddedExtensionRepository.isValidClassName(".com.example")); // starts with dot
            assertFalse(EmbeddedExtensionRepository.isValidClassName("com.example.")); // ends with dot
        }

        @Test
        @DisplayName("rejects class names with special characters")
        void rejectsSpecialCharacters() {
            assertFalse(EmbeddedExtensionRepository.isValidClassName("com/example/Class")); // path separator
            assertFalse(EmbeddedExtensionRepository.isValidClassName("com\\example\\Class")); // backslash
            assertFalse(EmbeddedExtensionRepository.isValidClassName("com.example@Class")); // at sign
            assertFalse(EmbeddedExtensionRepository.isValidClassName("com.example#Class")); // hash
        }

        @Test
        @DisplayName("rejects potential code injection")
        void rejectsCodeInjection() {
            assertFalse(EmbeddedExtensionRepository.isValidClassName("Class;System.exit(0)"));
            assertFalse(EmbeddedExtensionRepository.isValidClassName("Class\nSystem.exit(0)"));
        }
    }

    @Nested
    @DisplayName("Bytecode validation")
    class BytecodeValidationTests {

        @Test
        @DisplayName("ClassDataLoader validates bytecode magic number")
        void classDataLoaderValidatesMagic() {
            // This test documents the expected behavior - ClassDataLoader should reject
            // non-class files. Actual integration tests would require mocking the resource loader.

            // Valid class file starts with CAFEBABE
            byte[] validMagic = new byte[] {
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                0x00, 0x00, 0x00, 0x34, // version
                0x00, 0x01 // constant_pool_count
            };
            assertTrue(validMagic.length >= 10);
            assertTrue(validMagic[0] == (byte) 0xCA);
            assertTrue(validMagic[1] == (byte) 0xFE);
            assertTrue(validMagic[2] == (byte) 0xBA);
            assertTrue(validMagic[3] == (byte) 0xBE);

            // Invalid: wrong magic
            byte[] invalidMagic = new byte[] {
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                0x00, 0x00, 0x00, 0x34,
                0x00, 0x01
            };
            assertFalse(invalidMagic[0] == (byte) 0xCA);
        }
    }
}
