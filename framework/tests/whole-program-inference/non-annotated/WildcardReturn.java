// This test ensures that generated stub files handle methods that have a
// wildcard return type correctly.

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

class WildcardReturn {
    public static <T extends Comparable<? super T>> T max(Collection<T> coll) {
        return null;
    }

    public Set<Object> getCredentialIdsForUsername(String username) {
        return getRegistrationsByUsername(username).stream()
                .map(registration -> registration.toString())
                .collect(Collectors.toSet());
    }

    public Collection<Object> getRegistrationsByUsername(String username) {
        return null;
    }
}
