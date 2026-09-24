package ac.jester.anticheat.utils.common.arguments;

import java.util.function.Function;
import java.util.function.Predicate;

public record SystemArgument<T>(String key, Class<T> clazz, T value, boolean set,
                                Visibility visibility) {
    public boolean matches(Predicate<T> predicate) {
        return predicate.test(value);
    }

    public <K> K mapValue(Function<T, K> mapper, K otherwise) {
        try {
            return value == null ? otherwise : mapper.apply(value);
        } catch (Exception e) {
        }
        return otherwise;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SystemArgument<?> that = (SystemArgument<?>) o;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    public enum Visibility {
        VISIBLE,
        HIDDEN,
        SECRET
    }
}
