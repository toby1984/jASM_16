package de.codesourcery.jasm16.ide;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.io.IResource;

/**
 * Keeps track of a specific number of visited source code locations.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class NavigationHistory
{
    private static final Logger LOG = Logger.getLogger(NavigationHistory.class);
    
    public static final int DEFAULT_NAVIGATION_HISTORY_SIZE = 50;
    
    private final int maxSize;
    
    // @GuardedBy( stack )
    private final List<Location> stack = new ArrayList<>();
    // @GuardedBy( stack )    
    private int currentPtr = 0;
    
    public interface INavigationHistoryListener 
    {
        public void navigationHistoryChanged();
    }
    
    // @GuardedBy( listeners );
    private final List<INavigationHistoryListener> listeners = new ArrayList<>();
    
    public final static class Location 
    {
        private final IAssemblyProject project;
        private final IResource resource;
        private final int offset;
        
        public Location(IAssemblyProject project,IResource resource, int offset)
        {
            if ( project == null ) {
                throw new IllegalArgumentException("project must not be NULL.");
            }
            if ( resource == null ) {
                throw new IllegalArgumentException("resource must not be NULL.");
            }
            if ( offset < 0 ) {
                throw new IllegalArgumentException("Invalid offset "+offset);
            }
            this.project = project;
            this.resource = resource;
            this.offset = offset;
        }
        
        @Override
        public String toString()
        {
            return "Location[ project="+project+", offset="+offset+" , resource="+resource+"]";
        }
        
        public IResource getResource()
        {
            return resource;
        }
        
        public IAssemblyProject getProject()
        {
            return project;
        }
        
        public int getOffset()
        {
            return offset;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + offset;
            result = prime * result + ((resource == null) ? 0 : resource.getIdentifier().hashCode());
            return result;
        }

        @Override
        public boolean equals(Object o) 
        {
            if ( o == this ) {
                return true;
            }
            if ( o instanceof Location) {
                return this.offset == ((Location) o).offset &&
                        this.resource.getIdentifier().equals( ((Location) o).getResource().getIdentifier() );
            }
            return false; 
        }
    }
    
    public NavigationHistory() 
    {
        this( DEFAULT_NAVIGATION_HISTORY_SIZE );
    }
    
    public void addListener(INavigationHistoryListener listener) 
    {
        synchronized (listeners) {
            listeners.add( listener );
        }
    }
    
    public void removeListener(INavigationHistoryListener listener) 
    {
        synchronized (listeners) {
            listeners.remove( listener );
        }
    }
    
    private void notifyListeners() {
        
        System.out.println("\n----------- Navigation history -----------\n"+toString() );
        
        final List<INavigationHistoryListener> copy;
        synchronized (listeners) {
            copy = new ArrayList<>( listeners );
        }
        for ( INavigationHistoryListener l : copy ) {
            try {
                l.navigationHistoryChanged();
            } catch(Exception e) {
                LOG.error("notifyListeners(): Listener "+l+" failed",e);
            }
        }
    }
    
    public NavigationHistory(int maxSize) 
    {
        if ( maxSize < 0 ) {
            throw new IllegalArgumentException("Invalid maxSize "+maxSize);
        }        
        this.maxSize = maxSize;
    }
    
    public boolean isEmpty() {
        synchronized (stack) {
            return stack.isEmpty();
        }
    }
    
    /**
     * 
     * @param location
     * @return <code>true</code> if the location was added, <code>false</code> if it's already
     * present at the latest navigation history position
     */
    public boolean add(Location location) 
    {
        if ( location == null ) {
            throw new IllegalArgumentException("location must not be NULL.");
        }
        synchronized (stack) 
        {
            if ( ! stack.isEmpty() && stack.get( stack.size() -1 ).equals( location ) ) {
                return false;
            }
            if ( stack.size() == maxSize ) {
                stack.remove(0);
            }
            stack.add( location );
            currentPtr = stack.size()-1;
        }
        notifyListeners();
        return true;
    }
    
    public boolean clear() 
    {
        boolean containedEntries;
        synchronized (stack) 
        {
            containedEntries = ! stack.isEmpty();
            stack.clear();
            currentPtr=0;
        }
        if ( containedEntries ) {
            notifyListeners();
        }
        return containedEntries;
    }
    
    public Location goBack() 
    {
        boolean notifyListeners = true;
        try 
        {        
            synchronized (stack) 
            {
                if ( ! canGoBack() ) {
                    notifyListeners = false;
                    return null;
                }
                currentPtr--;
                return stack.get( currentPtr );
            }
        } 
        finally 
        {
            if ( notifyListeners ) {
                notifyListeners();
            }
        }        
    }
    
    public boolean canGoBack() {
        synchronized (stack) {
            return currentPtr > 0;
        }
    }    
    
    public Location goForward() 
    {
        boolean notifyListeners = true;
        try 
        {        
            synchronized (stack) 
            {
                if ( ! canGoForward() ) {
                    notifyListeners = false;                    
                    return null;
                }
                currentPtr++;
                return stack.get( currentPtr );
            }
        }
        finally 
        {
            if ( notifyListeners ) {
                notifyListeners();                
            }
        }        
    }    
    
    public boolean canGoForward() {
        synchronized (stack) {
            return currentPtr < (stack.size()-1);
        }
    }
    
    @Override
    public String toString()
    {
        final List<Location> copy;
        synchronized (stack) 
        {
            copy = new ArrayList<>(stack);        
        }
        
        String result = "";
        int i = 0;
        for (Iterator<Location> it = copy.iterator(); it.hasNext();i++) {
            Location loc = it.next();
            result += "["+( i == currentPtr ? "*" : " " )+"] "+loc;
            if ( it.hasNext() ) {
                result += "\n";
            }
        }
        result+= ("\n canGoBack: "+canGoBack()+" / canGoForward: "+canGoForward());
        return result;
    }
}