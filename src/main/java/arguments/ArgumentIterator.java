package arguments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ArgumentIterator implements Iterator<String> {
    private List<String> spaceSplit = new ArrayList<String>();
    private Iterator<String> basicIterator;
    
    public ArgumentIterator(String[] arguments) {
        this.spaceSplit = Arrays.asList(arguments);
        this.basicIterator = spaceSplit.iterator();
    }

    @Override
    public boolean hasNext() {
        return basicIterator.hasNext();
    }

    @Override
    public String next() {
        return basicIterator.next();
    }
}
