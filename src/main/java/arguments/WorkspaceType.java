package arguments;


public enum WorkspaceType {
    CI,     // Faster build but not suited for development
    DECOMP  // Much slower build but allows development out of it
}
