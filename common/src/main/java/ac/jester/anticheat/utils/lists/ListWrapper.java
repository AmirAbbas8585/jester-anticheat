package ac.jester.anticheat.utils.lists;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public abstract class ListWrapper<T> implements List<T> {
    protected final List<T> base;
}
