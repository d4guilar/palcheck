package com.palcheck.workforce.domain;

/**
 * One field of a partial update (JSON merge-patch semantics): absent means "leave unchanged", a value means "set",
 * and clear means "remove an optional value". The three cases must stay distinct, which a nullable field cannot do.
 */
public final class Patch<T> {

    private enum Kind { ABSENT, SET, CLEAR }

    private static final Patch<?> ABSENT = new Patch<>(Kind.ABSENT, null);
    private static final Patch<?> CLEAR = new Patch<>(Kind.CLEAR, null);

    private final Kind kind;
    private final T value;

    private Patch(Kind kind, T value) {
        this.kind = kind;
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public static <T> Patch<T> absent() {
        return (Patch<T>) ABSENT;
    }

    @SuppressWarnings("unchecked")
    public static <T> Patch<T> clear() {
        return (Patch<T>) CLEAR;
    }

    public static <T> Patch<T> set(T value) {
        return new Patch<>(Kind.SET, java.util.Objects.requireNonNull(value, "value"));
    }

    public boolean isAbsent() {
        return kind == Kind.ABSENT;
    }

    public boolean isClear() {
        return kind == Kind.CLEAR;
    }

    public boolean isSet() {
        return kind == Kind.SET;
    }

    /** The value to set; only meaningful when {@link #isSet()}. */
    public T value() {
        return value;
    }

    /** The current value if the patch leaves it alone, the new value if set, or null if cleared. */
    public T applyTo(T current) {
        return switch (kind) {
            case ABSENT -> current;
            case SET -> value;
            case CLEAR -> null;
        };
    }
}
