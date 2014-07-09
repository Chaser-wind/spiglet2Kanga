package sets;

import java.util.HashSet;
import java.util.Set;

public final class Sets {

	public static <T> Set<T> union(Set<T> a,Set<T> b){
		Set<T> union = new HashSet<T>();
		union.addAll(a);
		union.addAll(b);
		return union;
	}

	public static <T> Set<T> difference(Set<T> a, Set<T> b){
		Set<T> difference = new HashSet<T>();
		difference.addAll(a);
		difference.removeAll(b);
		return difference;
	}

	public static <T> boolean differ(Set<T> a,Set<T> b){
		return a.size() != b.size() || !a.containsAll(b) || !b.containsAll(a);
	}
}
