package org.coderclan.whistle;

import net.jqwik.api.Example;
import net.jqwik.api.Tag;
import org.coderclan.whistle.rdbms.*;

import java.util.Arrays;

/**
 * Property 35: Dead code removal — getBaseRetrieveSql() removed
 *
 * Validates: Requirements 2.1, 2.2
 *
 * Verifies that the dead {@code getBaseRetrieveSql(int)} method has been removed
 * from {@code AbstractRdbmsEventPersistenter} and all four subclasses, and that
 * {@code getOrderedBaseRetrieveSql(int)} is still declared on the abstract class.
 */
class DeadCodeRemovalProperties {

    private static final String REMOVED_METHOD = "getBaseRetrieveSql";
    private static final String RETAINED_METHOD = "getOrderedBaseRetrieveSql";

    /**
     * Checks whether a class declares a method with the given name and a single int parameter.
     */
    private boolean declaresMethod(Class<?> clazz, String methodName) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(methodName)
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == int.class);
    }

    /**
     * Property 35: Dead code removal — getBaseRetrieveSql() removed
     *
     * Verifies that getBaseRetrieveSql(int) is NOT declared on
     * AbstractRdbmsEventPersistenter or any of its four subclasses.
     *
     * Validates: Requirements 2.1, 2.2
     */
    @Example
    @Tag("Feature: whistle-event-system")
    @Tag("Property 35: Dead code removal - getBaseRetrieveSql() removed")
    boolean getBaseRetrieveSqlRemovedFromAllClasses() {
        Class<?>[] classes = {
                AbstractRdbmsEventPersistenter.class,
                MysqlEventPersistenter.class,
                PostgresqlEventPersistenter.class,
                OracleEventPersistenter.class,
                H2EventPersistenter.class
        };

        for (Class<?> clazz : classes) {
            assert !declaresMethod(clazz, REMOVED_METHOD) :
                    "getBaseRetrieveSql(int) should NOT be declared on " + clazz.getSimpleName();
        }

        return true;
    }

    /**
     * Verifies that getOrderedBaseRetrieveSql(int) IS still declared on
     * AbstractRdbmsEventPersistenter.
     *
     * Validates: Requirements 2.1, 2.2
     */
    @Example
    @Tag("Feature: whistle-event-system")
    @Tag("Property 35: Dead code removal - getBaseRetrieveSql() removed")
    boolean getOrderedBaseRetrieveSqlStillDeclared() {
        assert declaresMethod(AbstractRdbmsEventPersistenter.class, RETAINED_METHOD) :
                "getOrderedBaseRetrieveSql(int) should still be declared on AbstractRdbmsEventPersistenter";

        return true;
    }
}
